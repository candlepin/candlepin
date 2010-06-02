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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.BooleanUtils;
import org.fedoraproject.candlepin.client.CandlepinConsumerClient;
import org.fedoraproject.candlepin.client.model.EntitlementCertificate;
import org.fedoraproject.candlepin.client.model.Pool;
import org.fedoraproject.candlepin.client.model.ProductCertificate;

/**
 * RegisterCommand
 */
public class ListCommand extends PrivilegedCommand {

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

    protected final void execute(CommandLine cmdLine, CandlepinConsumerClient client) {
        if (cmdLine.hasOption("a")) {
            List<Pool> pools = client.listPools();
            if (pools.isEmpty()) {
                System.out.println("No availale subscription pools to list");
                return;
            }
            System.out.println("+-------------------------------------------" +
                "+\n\tInstalled Product Status\n" +
                "+-------------------------------------------+\n");

            for (Pool pool : pools) {
                System.out.printf("%-25s%s\n", "ProductName:", pool
                    .getProductName());
                System.out.printf("%-25s%s\n", "Product SKU:", pool
                    .getProductId());
                System.out.printf("%-25s%s\n", "PoolId:", pool.getId());
                System.out.printf("%-25s%d\n", "quantity:", pool.getQuantity());
                System.out.printf("%-25s%s\n", "Expires:", pool.getEndDate());
                System.out.println("\n");
            }
        }
        else if (cmdLine.hasOption("c")) {
            List<EntitlementCertificate> certs = client
                .getCurrentEntitlementCertificates();
            if (certs.isEmpty()) {
                System.out.println("No Consumed subscription pools to list");
                return;
            }
            for (EntitlementCertificate cert : certs) {
                System.out.printf("%-25s%s\n", "Name:", cert.getProductName());
                System.out.printf("%-25s%s\n", "SerialNumber:", cert
                    .getSerial());
                System.out.printf("%-25s%s\n", "Active:", BooleanUtils
                    .toStringTrueFalse(cert.isValid()));
                System.out.printf("%-25s%s\n", "Begins:", cert.getStartDate());
                System.out.printf("%-25s%s\n", "Ends:", cert.getEndDate());
                System.out.println("\n");
            }
        }
        else {
            // get product certificates and list them out.
            List<ProductCertificate> productCertificates = client
                .getInstalledProductCertificates();
            for (ProductCertificate pc : productCertificates) {
                System.out.printf("%-25s%s\n", "ProductName:", pc
                    .getProductName());
                System.out.printf("%-25s%s\n", "Status:",
                    pc.isInstalled() ? "Installed" : "Not Installed");
                System.out.printf("%-25s%s\n", "Expires:", pc.getEndDate());
                System.out.printf("%-25s%s\n", "Subscription:", pc
                    .isInstalled() ? pc.getEntitlementCertificate().getSerial() : "");
                System.out.println("\n");
            }

        }

    }

}
