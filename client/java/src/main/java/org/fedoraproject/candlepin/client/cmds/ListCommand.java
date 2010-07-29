/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.client.cmds;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.BooleanUtils;
import org.fedoraproject.candlepin.client.CandlepinClientFacade;
import org.fedoraproject.candlepin.client.model.EntitlementCertificate;
import org.fedoraproject.candlepin.client.model.Pool;
import org.fedoraproject.candlepin.client.model.Product;
import org.fedoraproject.candlepin.client.model.ProductCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RegisterCommand
 */
public class ListCommand extends PrivilegedCommand {

    private static final Logger L = LoggerFactory.getLogger(ListCommand.class);

    @Override
    public String getName() {
        return "list";
    }

    public String getDescription() {
        return "List available or consumer entitlement pools";
    }

    public Options getOptions() {
        Options opts = super.getOptions();
        opts.addOption("a", "available", false,
            "List the available Subscriptions");
        opts.addOption("c", "consumed", false,
            "List the consumed Subscriptions (default)");
        return opts;
    }

    protected final void execute(CommandLine cmdLine,
        CandlepinClientFacade client) {
        if (cmdLine.hasOption("a")) {
            printAvailableProducts(client);
        }
        if (cmdLine.hasOption("c")) {
            printConsumedProducts(client);
        }
        if (!cmdLine.hasOption("a") && !cmdLine.hasOption("c")) {
            printInstalledProductsStatus(client);
        }

    }

    /**
     * @param client
     */
    private void printInstalledProductsStatus(CandlepinClientFacade client) {
        L.info("Printing out already installed products on this system");
        // get product certificates and list them out.
        List<ProductCertificate> productCertificates = client
            .getInstalledProductCertificates();
        List<EntitlementCertificate> entitlementCertificates = client
            .getCurrentEntitlementCertificates();
        L.info("ProductCertificates #{} & EntitlementCertificates #{}",
            productCertificates.size(), entitlementCertificates.size());
        if (productCertificates.isEmpty() && entitlementCertificates.isEmpty()) {
            System.out.println("No installed Products to list");
            return;
        }

        toConsoleAndLogs("+-------------------------------------------" +
            "+\n\tInstalled Product Status\n" +
            "+-------------------------------------------+\n");
        Map<Product, EntitlementCertificate> prodToEntitlementMap = Utils
            .newMap();
        Set<Product> listedProducts = Utils.newSet();

        for (EntitlementCertificate certificate : entitlementCertificates) {
            for (Product product : certificate.getProducts()) {
                prodToEntitlementMap.put(product, certificate);
            }
        }

        for (ProductCertificate pc : productCertificates) {
            for (Product product : pc.getProducts()) {
                L.debug("Examining product within ProductCertificate #{} {}",
                    pc.getSerial(), product);
                if (listedProducts.contains(product)) {
                    // already printed out product. skip it.
                    continue;
                }
                if (prodToEntitlementMap.get(product) != null) {
                    String status = pc.isValid() ? "Subscribed" : "Expired";
                    printProductDetails(product.getName(), status, pc
                        .getEndDate().toString(), pc.getSerial().toString(),
                        prodToEntitlementMap.get(product).getOrder()
                            .getUsedQuantity());
                }
                else {
                    // product not subscribed yet.
                    printProductDetails(product.getName(), "Not Subscribed",
                        "", "", 0);
                }

                listedProducts.add(product);
            }
        }

        for (EntitlementCertificate certificate : entitlementCertificates) {
            for (Product product : certificate.getProducts()) {
                L.debug(
                    "Examining product within EntitlementCertificate #{} {}",
                    certificate.getSerial(), product);
                if (listedProducts.contains(product)) {
                    continue;
                }
                printProductDetails(product.getName(), "Not Installed",
                    certificate.getEndDate().toString(), certificate
                        .getSerial().toString(), certificate.getOrder()
                        .getQuantity());
                listedProducts.add(product);
            }
        }
    }

    private void printProductDetails(String productName, String status,
        String expDate, String serial, int quantity) {
        toConsoleAndLogs("%-25s%s\n", "ProductName:", productName);
        toConsoleAndLogs("%-25s%s\n", "Status:", status);
        toConsoleAndLogs("%-25s%s\n", "Expires:", expDate);
        toConsoleAndLogs("%-25s%s\n", "Subscription:", serial);
        toConsoleAndLogs("%-25s%s\n", "Quantity:", quantity);
        toConsoleAndLogs("\n");
    }

    /**
     * @param client
     */
    private void printAvailableProducts(CandlepinClientFacade client) {
        List<Pool> pools = client.listPools();
        if (pools.isEmpty()) {
            toConsoleAndLogs("No Availale subscription pools to list");
            return;
        }
        toConsoleAndLogs("+-------------------------------------------" +
            "+\n\tAvailable Subscriptions\n" + 
            "+-------------------------------------------+\n");

        for (Pool pool : pools) {
            toConsoleAndLogs("%-25s%s\n", "ProductName:", pool.getProductName());
            toConsoleAndLogs("%-25s%s\n", "Product SKU:", pool.getProductId());
            toConsoleAndLogs("%-25s%s\n", "PoolId:", pool.getId());
            toConsoleAndLogs("%-25s%d\n", "quantity:", pool.getQuantity());
            toConsoleAndLogs("%-25s%s\n", "Expires:", pool.getEndDate());
            System.out.println("\n");
        }
    }

    /**
     * @param client
     */
    private void printConsumedProducts(CandlepinClientFacade client) {
        List<EntitlementCertificate> certs = client
            .getCurrentEntitlementCertificates();
        if (certs.isEmpty()) {
            System.out.println("No Consumed subscription pools to list");
            return;
        }
        System.out.println("+-------------------------------------------" + 
            "+\n\tConsumed Product Subscriptions\n" + 
            "+-------------------------------------------+\n");
        for (EntitlementCertificate cert : certs) {
            for (Product product : cert.getProducts()) {
                toConsoleAndLogs("%-25s%s\n", "Name:", product.getName());
                toConsoleAndLogs("%-25s%s\n", "SerialNumber:", cert.getSerial());
                toConsoleAndLogs("%-25s%s\n", "Active:", BooleanUtils
                    .toStringTrueFalse(cert.isValid()));
                toConsoleAndLogs("%-25s%s\n", "Begins:", cert.getStartDate());
                toConsoleAndLogs("%-25s%s\n", "Ends:", cert.getEndDate());
                System.out.println("\n");
            }
        }
    }

    private void toConsoleAndLogs(String msg, Object... args) {
        if (args.length > 0) {
            msg = String.format(msg, args);
        }
        System.out.print(msg);
        L.debug(msg);
    }

}
