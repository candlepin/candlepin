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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.ArrayUtils;
import org.fedoraproject.candlepin.client.CandlepinConsumerClient;
import static org.apache.commons.lang.ArrayUtils.isEmpty;
/**
 * RegisterCommand
 */
public class SubscribeCommand extends BaseCommand {

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
        return opts;
    }

  
    @Override
    public void execute(CommandLine cmdLine) {
        String [] pools = cmdLine.getOptionValues("p");
        String [] products = cmdLine.getOptionValues("pr");
        String [] regTokens = cmdLine.getOptionValues("r");
        if(!this.getClient().isRegistered()){
        	System.out.println("This system is currently not registered.");
        	return;
        }
        if (isEmpty(pools) && isEmpty(products) && isEmpty(regTokens)) {
            System.err.println("Error: Need either --product or --regtoken, Try --help");
            return;
        }
        CandlepinConsumerClient client = this.getClient();
        
        try {
			if(!isEmpty(pools)){
				for(String pool: pools){
					client.bindByPool(Long.decode(pool));
				}
			}
			
			if(!isEmpty(products)){
				for(String product: products){
					client.bindByProductId(product);
				}
			}
			
			if(!isEmpty(regTokens)){
				for(String token: regTokens){
					client.bindByRegNumber(token);
				}
			}
		} catch (Exception e) {
			System.err.println("Unable to subscribe!");
			e.printStackTrace();
			return;
		}
        client.updateEntitlementCertificates();
    }

}
