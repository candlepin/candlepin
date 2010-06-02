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
import org.fedoraproject.candlepin.client.OperationResult;
import static org.apache.commons.lang.StringUtils.*;

/**
 * RegisterCommand.
 */
public class RegisterCommand extends BaseCommand {

    /*
     * (non-Javadoc)
     * @see org.fedoraproject.candlepin.client.cmds.BaseCommand#getName()
     */
    @Override
    public String getName() {
        return "register";
    }

    /*
     * (non-Javadoc)
     * @see org.fedoraproject.candlepin.client.cmds.BaseCommand#getDescription()
     */
    public String getDescription() {
        return "Register the client to a Unified Entitlement Platform.";
    }

    /*
     * (non-Javadoc)
     * @see org.fedoraproject.candlepin.client.cmds.BaseCommand#getOptions()
     */
    public Options getOptions() {
        Options opts = super.getOptions();
        opts.addOption("u", "username", true, "Specify a username");
        opts.addOption("p", "password", true, "Specify a password");
        opts.addOption("id", "consumerid", true,
            "Register to an Existing consumer");
        opts.addOption("a", "autosubscribe", true,
            "Automatically subscribe this system to compatible subscriptions.");
        opts.addOption("f", "force", false,
            "Register the system even if it is already registered");
        return opts;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.fedoraproject.candlepin.client.cmds.BaseCommand#execute(org.apache
     * .commons.cli.CommandLine)
     */
    public void execute(CommandLine cmdLine) {
        String username = cmdLine.getOptionValue("u");
        String password = cmdLine.getOptionValue("p");
        String consumerId = cmdLine.getOptionValue("id");
        boolean force = cmdLine.hasOption("f");
        boolean bothUserPassPresent = isNotEmpty(username) &&
            isNotEmpty(password);
        if (!bothUserPassPresent && consumerId == null) {
            System.err
                .println("Error: username and password or " +
                    "consumerid are required to register,try --help.\n");
            return;
        }
        else if (bothUserPassPresent && consumerId != null) {
            System.err
                .println("Error1: username and password or" +
                    " consumerid are required, not both. try --help.\n");
            return;
        }
        CandlepinConsumerClient client = this.getClient();

        if (client.isRegistered() && !force) {
            System.out
                .println("This system is already registered. Use --force to override");
            return;
        }
        // register based on client id. Go ahead with invocation
        if (consumerId != null) {
            OperationResult reason = this.client
                .registerExistingCustomerWithId(consumerId);
            printMsgs(consumerId, reason);
            return;
        }
        else {
            // register with username and password.
            try {
                String uuid = client.register(username, password, "JavaClient",
                    "system");
                System.out.println("Registered with UUID: " + uuid);
            }
            catch (Exception e) {
                e.printStackTrace();
                printMsgs(consumerId, OperationResult.UNKNOWN);
            }

        }

    }

    /**
     * Prints the msgs.
     *
     * @param consumerId the consumer id
     * @param reason the reason
     */
    private void printMsgs(String consumerId, OperationResult reason) {
        switch (reason) {
            case NOT_A_FAILURE:
                System.out
                    .println("Registered with UUID: " + this.client.getUUID());
                break;
            case ERROR_WHILE_SAVING_CERTIFICATES:
                System.err
                    .println("Error while trying to save certificate and keys to disk.");
                break;
            case INVALID_UUID:
                System.err
                    .println("Unable to register with consumer " + consumerId);
                break;
            default:
                System.err.println("Registration failed due to unknown reason." +
                    " Please try again.");
                break;
        }
    }

}
