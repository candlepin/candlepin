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
import org.fedoraproject.candlepin.client.CandlepinConsumerClient;

/**
 * RegisterCommand
 */
public class RegisterCommand extends BaseCommand {

    @Override
    public String getName() {
        return "register";
    }


    public String getDescription() {
        return "Register the client to a Unified Entitlement Platform.";
    }
    
    public Options getOptions() {
        Options opts = super.getOptions();
        opts.addOption("u", "username", true, "The username to register with");
        opts.addOption("p", "password", true, "The password to use");
        opts.addOption("f", "force", false, "Force a registration even if one exists");
        return opts;
    }
    
    public void execute(CommandLine cmdLine) {
        String username = cmdLine.getOptionValue("u");
        String password = cmdLine.getOptionValue("p");
        boolean force = cmdLine.hasOption("f");        
        if ((username == null) || (password == null)) {
            System.err.println("Both username and password must be provided");
            return;
        }
        CandlepinConsumerClient client = 
            new CandlepinConsumerClient("http://localhost:8080/candlepin");
        
        if (client.isRegistered() && !force) {
            System.out.println("Already registered. Use force to re-register");
            return;
        }
        String uuid = client.register(username, password, "JavaClient", "system");
        System.out.println("Registered with UUID: " + uuid);
        
    }

}
