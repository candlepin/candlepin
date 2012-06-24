/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.client.cmds;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.StringUtils;
import org.candlepin.client.CandlepinClientFacade;
import org.candlepin.client.model.Product;
import org.candlepin.client.model.ProductCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RegisterCommand.
 */
public class RegisterCommand extends BaseCommand {

    private static final Logger L = LoggerFactory
        .getLogger(RegisterCommand.class);

    /*
     * (non-Javadoc)
     * @see org.candlepin.client.cmds.BaseCommand#getName()
     */
    @Override
    public String getName() {
        return "register";
    }

    /*
     * (non-Javadoc)
     * @see org.candlepin.client.cmds.BaseCommand#getDescription()
     */
    public String getDescription() {
        return "Register the client to a Unified Entitlement Platform.";
    }

    /*
     * (non-Javadoc)
     * @see org.candlepin.client.cmds.BaseCommand#getOptions()
     */
    public Options getOptions() {
        Options opts = super.getOptions();
        opts.addOption("u", "username", true, "Specify a username");
        opts.addOption("p", "password", true, "Specify a password");
        opts.addOption("n", "name", true, "Specify a name for the consumer");
        opts.addOption("t", "type", true,
            "Specify a type for the consumer, defaults to system");
        opts.addOption("id", "consumerid", true,
            "Register to an Existing consumer");
        opts.addOption("a", "autosubscribe", false,
            "Automatically subscribe this system to compatible subscriptions.");
        opts.addOption("f", "force", false,
            "Register the system even if it is already registered");
        return opts;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.candlepin.client.cmds.BaseCommand#execute(org.apache
     * .commons.cli.CommandLine)
     */
    public void execute(CommandLine cmdLine) {
        String username = cmdLine.getOptionValue("u");
        String password = cmdLine.getOptionValue("p");
        String consumerId = cmdLine.getOptionValue("id");
        String consumerName = cmdLine.getOptionValue("n");
        String consumerType = cmdLine.getOptionValue("t");

        if (consumerType == null) {
            consumerType = "system";
        }
        if (consumerName == null) {
            consumerName = username;
        }
        boolean force = cmdLine.hasOption("f");
        boolean bothUserPassPresent = isNotEmpty(username) &&
            isNotEmpty(password);
        boolean autosubscribe = StringUtils.isNotEmpty(cmdLine
            .getOptionValue("a"));
        if (!bothUserPassPresent && consumerId == null) {
            System.err.println("Error: username and password or " +
                "consumerid are required to register,try --help.\n");
            return;
        }
        CandlepinClientFacade client = this.getClient();

        if (client.isRegistered() && !force) {
            System.out
                .println("This system is already registered. Use --force to override");
            return;
        }
        // register based on client id. Go ahead with invocation
        if (consumerId != null) {
            this.client.registerExisting(username, password, consumerId);
            autoSubscribe(autosubscribe);
            return;
        }
        else {
            // register with username and password.
            String uuid = client.register(username, password, consumerName,
                consumerType);
            System.out.println("Registered with UUID: " + uuid);
            autoSubscribe(autosubscribe);

        }

    }

    private void autoSubscribe(boolean subscribeAutomatically) {
        if (this.client.isRegistered()) {
            L.info("Trying to autosubscribe products for customer: {}",
                this.client.getUUID());
            List<ProductCertificate> productCertificates = this.client
                .getInstalledProductCertificates();
            for (ProductCertificate pc : productCertificates) {
                for (Product product : pc.getProducts()) {
                    try {
                        System.out.printf("\nTrying to bind product %s : %d",
                            product.getName(), product.getHash());
                        L.info("Trying to bind product: {}", product);
                        this.client.bindByProductId(String.valueOf(product
                            .getHash()), 1);
                        System.out
                            .printf(
                                "\nAutomatically subscribed the machine to product %s",
                                product.getName());
                        L.info("Automagically binded product: {}", product);
                    }
                    catch (Exception e) {
                        System.out
                            .printf(
                                "\nWarning: Unable to auto subscribe the machine to %s",
                                product.getName());
                        L.warn("Unable to autosubscribe the machine to {}",
                            product);
                    }
                }
            }
        }
    }

}
