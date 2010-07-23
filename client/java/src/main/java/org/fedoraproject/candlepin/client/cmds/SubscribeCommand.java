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

import static org.apache.commons.lang.ArrayUtils.isEmpty;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.fedoraproject.candlepin.client.CandlepinClientFacade;

/**
 * RegisterCommand
 */
public class SubscribeCommand extends PrivilegedCommand {

    @Override
    public String getName() {
        return "subscribe";
    }

    public String getDescription() {
        return "Create an entitlement for this consumer.";
    }

    @Override
    public Options getOptions() {
        Options opts = super.getOptions();
        opts.addOption("p", "pool", true, "Subscription Pool Id");
        opts.addOption("pr", "product", true, "product ID");
        opts.addOption("r", "regtoken", true, "regtoken");
        opts.addOption("q", "quantity", true,
            "The quantities of pool/product/regtoken used");
        opts.addOption("e", "email", true,
            "email address which will receive confirmation e-mail "
                + "(applies for only --regtoken)");
        opts.addOption("l", "locale", true, "Preferred locale of email"
            + "(applies for only --regtoken)");
        return opts;
    }

    @Override
    protected void execute(CommandLine cmdLine, CandlepinClientFacade client) {
        String[] pools = cmdLine.getOptionValues("p");
        String[] products = cmdLine.getOptionValues("pr");
        String[] regTokens = cmdLine.getOptionValues("r");
        int[] quantity = Utils.toInt(cmdLine.getOptionValues("q"));
        String email = cmdLine.getOptionValue("e");
        String defLocale = StringUtils.defaultIfEmpty(cmdLine
            .getOptionValue("l"), SystemUtils.USER_LANGUAGE);
        int iter = 0;
        if (!this.getClient().isRegistered()) {
            System.out.println("This system is currently not registered.");
            return;
        }
        if (isEmpty(pools) && isEmpty(products) && isEmpty(regTokens)) {
            System.err.println("Error: Need either --product or --pool"
                + " or --regtoken, Try --help");
            return;
        }
        if (!isEmpty(pools)) {
            for (String pool : pools) {
                client.bindByPool(Long.decode(pool.trim()), Utils.getSafeInt(
                    quantity, iter++, 1));
            }
        }

        if (!isEmpty(products)) {
            for (String product : products) {
                client.bindByProductId(product, Utils.getSafeInt(quantity,
                    iter++, 1));
            }
        }

        if (!isEmpty(regTokens)) {
            for (String token : regTokens) {
                if (StringUtils.isNotBlank(email)) {
                    client.bindByRegNumber(token, Utils.getSafeInt(quantity,
                        iter++, 1), email, defLocale);
                }
                else {
                    client.bindByRegNumber(token, Utils.getSafeInt(quantity,
                        iter++, 1));
                }
            }
        }
        client.updateEntitlementCertificates();
    }

}
