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

import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

/**
 * UpdateFactsCommand
 */
public class FactsCommand extends BaseCommand {

    @Override
    public String getName() {
        return "facts";
    }

    @Override
    public String getDescription() {
        return "View or refresh the hardware information about this consumer.";
    }
    
    @Override
    public Options getOptions() {
        Options opts = super.getOptions();
        
        opts.addOption("l", "list", false,
            "List the current hardware facts");
        opts.addOption("u", "update", false,
            "Updates the hardware information on the Unified Entitlement Platform.");
        return opts;
    }

    @Override
    public void execute(CommandLine cmdLine) {
        if (cmdLine.hasOption("l")) {
            Map<String, String> facts = Utils.getHardwareFacts();
            int keyLength = findLongestKey(facts);
            
            System.out.println("--- Hardware Facts ---");
            for (Entry<String, String> entry : Utils.getHardwareFacts().entrySet()) {
                String key = padRight(entry.getKey(), keyLength);
                
                System.out.println(key + " = " + entry.getValue());
            }
        }
        else if (cmdLine.hasOption("u")) {
            if (this.getClient().updateConsumer()) {
                System.out.println("Hardware facts updated.");
            }
        }
    }
    
    private int findLongestKey(Map<String, String> map) {
        int longest = 0;
        
        for (String key : map.keySet()) {
            longest = Math.max(longest, key.length());
        }
        
        return longest;
    }

    private String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

}
