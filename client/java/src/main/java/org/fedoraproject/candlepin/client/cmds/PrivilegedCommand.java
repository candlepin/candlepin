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
import org.fedoraproject.candlepin.client.CandlepinConsumerClient;

/**
 * PrivilegedCommand - command which can be executed only by registered customers.
 */
public abstract class PrivilegedCommand extends BaseCommand {

    /*
     * (non-Javadoc)
     * @see
     * org.fedoraproject.candlepin.client.cmds.BaseCommand#execute(org.apache
     * .commons.cli.CommandLine)
     */
    @Override
    public final void execute(CommandLine cmdLine) {
        CandlepinConsumerClient client = this.getClient();
        if (!client.isRegistered()) {
            System.out.println("This system is currently not registered.");
            return;
        }
        this.execute(cmdLine, client);
    }

    protected abstract void execute(CommandLine cmdLine, CandlepinConsumerClient client);
}
