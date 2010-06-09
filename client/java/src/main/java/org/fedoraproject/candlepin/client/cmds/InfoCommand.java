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
import org.fedoraproject.candlepin.client.CandlepinClientFacade;
import org.fedoraproject.candlepin.client.model.EntitlementCertificate;

/**
 * RegisterCommand
 */
public class InfoCommand extends BaseCommand {

    @Override
    public String getName() {
        return "info";
    }

    public String getDescription() {
        return "Dump information about the consumer";
    }

    public Options getOptions() {
        Options opts = super.getOptions();
        return opts;
    }

    public void execute(CommandLine cmdLine) {
        CandlepinClientFacade client = this.getClient();

        if (client.isRegistered()) {
            System.out.println("Registered as consumer: " + client.getUUID());
            List<EntitlementCertificate> certs = client
                .getCurrentEntitlementCertificates();
            if (certs.size() > 0) {
                System.out.println(String.format(
                    "There are %d current subsriptions", certs.size()));
                System.out.println(String.format("  %-20s %-30s %-25s %-25s",
                    "Serial #", "Product Name", "Start Date", "End Dae"));
/*                for (EntitlementCertificate cert : certs) {
                    System.out.println(String.format(
                        "  %-20s %-30s %-25s %-25s", cert.getSerial(), cert
                            .getProductName(), cert.getStartDate(), cert
                            .getEndDate()));
                }*/
            }
            else {
                System.out.println("No current subscriptions");
            }
        }
        else {
            System.out.println("Not registered");
        }
    }

}
