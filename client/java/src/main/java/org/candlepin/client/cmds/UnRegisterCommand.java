/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.candlepin.client.CandlepinClientFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class UnRegisterCommand.
 */
public class UnRegisterCommand extends PrivilegedCommand {

    private static final Logger L = LoggerFactory
        .getLogger(UnRegisterCommand.class);

    /*
     * (non-Javadoc)
     * @see org.candlepin.client.cmds.BaseCommand#getDescription()
     */
    @Override
    public String getDescription() {
        return "unregister";
    }

    /*
     * (non-Javadoc)
     * @see org.candlepin.client.cmds.BaseCommand#getName()
     */
    @Override
    public String getName() {
        return "unregister";
    }

    /*
     * (non-Javadoc)
     * @see
     * org.candlepin.client.cmds.BaseCommand#execute(org.apache
     * .commons.cli.CommandLine)
     */
    @Override
    protected void execute(CommandLine cmdLine, CandlepinClientFacade client) {
        L.info("Unregistering customer: {}", client.getUUID());
        client.unRegister();
    }

    /*
     * (non-Javadoc)
     * @see org.candlepin.client.cmds.BaseCommand#getOptions()
     */
    @Override
    public Options getOptions() {
        Options opts = super.getOptions();
        opts.addOption("debug", true, "debug level"); // flag ignored.
        return opts;
    }

}
