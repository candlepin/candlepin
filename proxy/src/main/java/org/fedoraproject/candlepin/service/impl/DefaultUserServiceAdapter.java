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
package org.fedoraproject.candlepin.service.impl;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

/**
 * A simple config-based user service.
 *
 * User names, passwords and Owners can be entered in /etc/candlepin/candlepin.conf in
 * the following manner:
 *
 * auth.user.<user_name>=<user_password>:<owner_name>
 *
 * For example:
 * auth.user.bill=billspassword:IBM
 * auth.user.adam=adamspassword:NC State University
 *
 * Validation will reject any username/password/owner combination that is not defined
 * in this manner.
 *
 * There is one critical exception, however.  If no username/password:org combinations
 * are defined, then no validation occurs and <code>validateUser</code> always
 * returns <code>true</code> -- effectively acting as a passthrough.
 */
public class DefaultUserServiceAdapter implements UserServiceAdapter {

    private static final String USER_PASS_PREFIX = "auth.user.";

    private Map<String, String> passwords;
    private Map<String, String> owners;

    /**
     * Creates a new instace, using the {@link Config} to retrieve username/password
     * pairs.
     *
     * @param config the Config that provides valid authentication credentials
     */
    @Inject
    public DefaultUserServiceAdapter(Config config) {
        this.passwords = new HashMap<String, String>();
        this.owners = new HashMap<String, String>();
        Map<String, String> authConfig = config.configurationWithPrefix(USER_PASS_PREFIX);

        // strip off the prefix 
        for (String prefix : authConfig.keySet()) {
            String user = prefix.substring(USER_PASS_PREFIX.length());

            String[] passwordOrg = authConfig.get(prefix).trim().split(":");

            this.passwords.put(user, passwordOrg[0]);

            // check if org is specified
            if (passwordOrg.length > 1) {
                this.owners.put(user, passwordOrg[1]);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param username
     * @param password
     * @return <code>true</code> 
     */
    @Override
    public boolean validateUser(String username, String password) {

        if (!this.passwords.isEmpty()) {

            if (username != null && password != null) {
                return password.equals(this.passwords.get(username));
            }
            else {
                // neither can be null if there are passwords to check
                return false;
            }
        }

        // if no username/password pairs were defined, then this just becomes
        // a passthrough
        return true;
    }

    @Override
    public String getOwnerName(String username) {
        String ownerName = this.owners.get(username);

        //TODO: This is completely temporary!
        //      Need to figure out how to do this properly
        if (ownerName == null) {
            ownerName = "Spacewalk Public Cert";
        }

        return ownerName;
    }

    @Override
    public List<Role> getRoles(String username) {
        return Arrays.asList(new Role[] {Role.OWNER_ADMIN});
    }

}
