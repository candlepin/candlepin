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
import org.fedoraproject.candlepin.client.CandlepinConsumerClient;
import org.fedoraproject.candlepin.client.model.EntitlementCertificate;
import org.fedoraproject.candlepin.client.model.Pool;

/**
 * RegisterCommand
 */
public class ListCommand extends BaseCommand {

    @Override
    public String getName() {
        return "list";
    }

    public String getDescription() {
        return "List available or consumer entitlement pools";
    }

    public Options getOptions() {
        Options opts = super.getOptions();
        opts.addOption("a", "available", false, "List the available Subscriptions") ;
        opts.addOption("c", "consumed", false, "List the consumed Subscriptions (default)") ;        
        return opts;
    }

    public void execute(CommandLine cmdLine) {
        CandlepinConsumerClient client = this.getClient();

        if (!client.isRegistered()) {
            System.out
                .println("You must register first in order " +
                "to list the pools you can consume");
            return;
        }

        if (cmdLine.hasOption("a")) {
            List<Pool> pools = client.listPools();
            if(pools.isEmpty()){
            	System.out.println("No availale subscription pools to list");
            	return;
            }
            System.out.println("+-------------------------------------------+\n\tInstalled Product Status\n"
            		+ "+-------------------------------------------+\n");
          /*  System.out.println(String.format("%-10s %-60s %-10s %-20s %-20s", "ID",
                "Name", "Quantity", "Begin", "End"));*/
            for (Pool pool : pools) {
            	System.out.printf("%-25s%s\n", "ProductName:", pool.getProductName());
            //	System.out.printf("%-25s%s\n", "Status:", pool.getSourceEntitlement().);
            	System.out.printf("%-25s%s\n", "Expires:", pool.getEndDate());
            	System.out.printf("%-25s%d\n", "Subscription:", pool.getSubscriptionId());
            	System.out.println("\n");
     /*       	
                System.out.printf("%-10d %-60s %-10s %-20tF %-20tF\n",
                    pool.getId(), pool.getProductName(), pool.getQuantity(), pool
                        .getStartDate(), pool.getEndDate());*/
            }
        } 
        else  {
            List<EntitlementCertificate> certs = client.getCurrentEntitlementCertificates();
            if(certs.isEmpty()){
            	System.out.println("No Consumed subscription pools to list");
            	return;
            }
            System.out.printf("%-10s %-30s %-20s %-20s\n", "Serial",
                "Name", "Begin", "End");
            for (EntitlementCertificate cert : certs) {
                System.out.printf("%-10s %-30s %-20tF %-20tF\n",
                    cert.getSerial().toString(), cert.getProductName(), 
                    cert.getStartDate(), cert.getEndDate());
            }
        }

    }

}
