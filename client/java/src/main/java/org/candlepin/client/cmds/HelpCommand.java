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
package org.candlepin.client.cmds;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;

/**
 * HelpCommand
 */
public class HelpCommand extends BaseCommand {
    protected HashMap<String, BaseCommand> cmds;

    public HelpCommand(HashMap<String, BaseCommand> cmds) {
        this.cmds = cmds;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Generate this help message";
    }

    public void execute(CommandLine cmdLine) {
        System.out
            .println("Command Line should be CLIMain MODULENAME [options].");
        System.out.println("\nModules include:");
        String[] commandNames = cmds.keySet().toArray(new String[0]);
        Arrays.sort(commandNames);
        for (String cmdName : commandNames) {
            BaseCommand cmd = cmds.get(cmdName);
            System.out.println(String.format("     %-15s %s", cmd.getName(),
                cmd.getDescription()));
        }
        Properties properties = Utils.getDefaultProperties();
        for (Object key : properties.keySet()) {
            System.out.printf("\t%s=%s\n", key, properties.get(key));
        }
    }
}
