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
        // opts.addOption("u", "username", true,
        // "The username to register with");
        // opts.addOption("p", "password", true, "The password to use");
        // opts.addOption("f", "force", false,
        // "Force a registration even if one exists");
        return opts;
    }

    public void execute(CommandLine cmdLine) {
        CandlepinConsumerClient client = this.getClient();

        if (!client.isRegistered()) {
            System.out
            .println("You must register first in order to list the pools you can consume");
            return;
        }

        List<Pool> pools = client.listPools();
        System.out.println(String.format("%-10s %-30s %-10s %-20s %-20s", "ID",
            "Name", "Quantity", "Begin", "End"));
        for (Pool pool : pools) {
            System.out.println(String.format("%-10d %-30s %-10s %-20tF %-20tF",
                pool.getId(), pool.getProductName(), pool.getQuantity(), pool
                    .getStartDate(), pool.getEndDate()));
        }

    }

}
