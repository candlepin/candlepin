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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

/**
 * A simple config-based user service.
 *
 * User names, passwords and Owners can be entered in /etc/candlepin/candlepin.conf in
 * the following manner:
 *
 * auth.user.<user_name>=<user_password>:<owner_name>:role1,role2
 *
 * For example:
 * auth.user.bill=billspassword:IBM
 * auth.user.adam=adamspassword:NC State University:superadmin
 *
 * Validation will reject any username/password/owner combination that is not defined
 * in this manner.
 *
 * There is one critical exception, however.  If no username/password:org combinations
 * are defined, then no validation occurs and <code>validateUser</code> always
 * returns <code>true</code> -- effectively acting as a passthrough.
 */
public class ConfigUserServiceAdapter implements UserServiceAdapter {

    private static final String USER_PASS_PREFIX = "auth.user.";
    private static final String DEFAULT_ORG = "Default Org";

    private Map<String, String> userPasswords;
    private Map<String, String> userOwners;
    private Map<String, Set<Role>> userRoles;
    private OwnerCurator ownerCurator;

    private static Logger log = Logger.getLogger(ConfigUserServiceAdapter.class);
    
    /**
     * Creates a new instance, using the {@link Config} to retrieve username/password
     * pairs.
     *
     * @param config the Config that provides valid authentication credentials
     */
    @Inject
    public ConfigUserServiceAdapter(Config config, OwnerCurator ownerCurator) {

        this.userPasswords = new HashMap<String, String>();
        this.userOwners = new HashMap<String, String>();
        this.userRoles = new HashMap<String, Set<Role>>();
        this.ownerCurator = ownerCurator;
        Map<String, String> authConfig = config.configurationWithPrefix(USER_PASS_PREFIX);

        // strip off the prefix 
        for (String prefix : authConfig.keySet()) {
            String user = prefix.substring(USER_PASS_PREFIX.length());

            String[] passwordOrg = authConfig.get(prefix).trim().split(":");


            this.userPasswords.put(user, passwordOrg[0]);
            this.userRoles.put(user, new HashSet<Role>());

            // check if org is specified
            if (passwordOrg.length > 1) {
                createOrgIfNeeded(passwordOrg[1]);
                this.userOwners.put(user, passwordOrg[1]);
            }
            else {
                createOrgIfNeeded(DEFAULT_ORG);
                this.userOwners.put(user, DEFAULT_ORG);
            }

            // check if roles are specified:
            if (passwordOrg.length > 2) {
                String [] roles = passwordOrg[2].trim().split(",");
                for (String role : roles) {
                    if (role.toLowerCase().equals("consumer")) {
                        userRoles.get(user).add(Role.CONSUMER);
                    }
                    else if (role.toLowerCase().equals("owneradmin")) {
                        userRoles.get(user).add(Role.OWNER_ADMIN);
                    }
                    else if (role.toLowerCase().equals("superadmin")) {
                        userRoles.get(user).add(Role.SUPER_ADMIN);
                    }
                    else {
                        throw new RuntimeException("Unknown role in candlepin.conf: " +
                            role);
                    }
                    log.debug("Added role for " + user + ": " + role);
                }
            }
        }
    }
    
    private void createOrgIfNeeded(String ownerName) {
        Owner owner = ownerCurator.lookupByKey(ownerName);
        if (owner == null) {
            log.info("Creating new owner: " + ownerName);
            Owner o = new Owner(ownerName, ownerName);
            ownerCurator.create(o);
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

        if (!this.userPasswords.isEmpty()) {

            if (username != null && password != null) {
                return password.equals(this.userPasswords.get(username));
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
    public Owner getOwner(String username) {
        String ownerName = this.userOwners.get(username);

        if (ownerName == null) {
            ownerName = DEFAULT_ORG;
        }

        return new Owner(ownerName, ownerName);
    }

    @Override
    public List<Role> getRoles(String username) {
        Set<Role> roles = userRoles.get(username);
        if (roles == null) {
            roles = new HashSet<Role>();
        }
        return new LinkedList<Role>(roles);
    }

    @Override
    public User createUser(User user) {
        throw new UnsupportedOperationException(
            "This implementation does not support creating new Users!");
    }

    @Override
    public void deleteUser(User user) {
        throw new UnsupportedOperationException(
            "This implementation does not support deleting Users!");
        
    }

    @Override
    public List<User> listByOwner(Owner owner) {
        // TODO Auto-generated method stub
        return new LinkedList<User>();
    }

}
