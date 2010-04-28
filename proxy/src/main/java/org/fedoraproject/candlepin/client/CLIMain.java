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
package org.fedoraproject.candlepin.client;

import java.security.Security;
import java.util.HashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fedoraproject.candlepin.client.cmds.BaseCommand;
import org.fedoraproject.candlepin.client.cmds.HelpCommand;
import org.fedoraproject.candlepin.client.cmds.ListCommand;
import org.fedoraproject.candlepin.client.cmds.RegisterCommand;
import org.fedoraproject.candlepin.client.cmds.SubscribeCommand;
import org.fedoraproject.candlepin.client.cmds.UpdateCommand;

/**
 * ClientMain
 */
public class CLIMain {
    protected HashMap<String, BaseCommand> cmds = new HashMap<String, BaseCommand>();

    protected CLIMain() {
        registerCommands();
        initializeClient();
    }

    protected void registerCommands() {
        // First, create the client we will need to use
        try {
            Class[] commands = { RegisterCommand.class, ListCommand.class,
                SubscribeCommand.class, UpdateCommand.class };
            for (Class cmdClass : commands) {
                BaseCommand cmd = (BaseCommand) cmdClass.newInstance();
                cmds.put(cmd.getName(), cmd);
            }
            // Now add the help command
            cmds.put("help", new HelpCommand(cmds));
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }

    protected void initializeClient() {
        CandlepinConsumerClient client = new CandlepinConsumerClient(
            "https://localhost:8443/candlepin");
        for (BaseCommand cmd : cmds.values()) {
            cmd.setClient(client);
        }
    }

    protected BaseCommand getCommand(String[] args) {
        // Get the first item which does not start with a -
        // and assume it is the module name
        BaseCommand cmd = null;
        for (String arg : args) {
            if (!arg.startsWith("-")) {
                if (cmds.containsKey(arg)) {
                    cmd = cmds.get(arg);
                    break;
                }
            }
        }
        return cmd;
    }

    protected void execute(String[] args) {
        BaseCommand cmd = this.getCommand(args);
        if (cmd == null) {
            System.out.println("No command was specified");
            cmd = cmds.get("help");
        }
        try {
            CommandLine cmdLine = cmd.getCommandLine(args);
            if (cmdLine.hasOption("h")) {
                cmd.generateHelp();
            }
            cmd.execute(cmdLine);
        }
        catch (ParseException e) {
            cmd.generateHelp();
        }
    }

    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.trustStore",
            "/home/bkearney/tomcat6/conf/keystore");
        Security.addProvider(new BouncyCastleProvider());

        CLIMain cli = new CLIMain();
        cli.execute(args);

    }
}
