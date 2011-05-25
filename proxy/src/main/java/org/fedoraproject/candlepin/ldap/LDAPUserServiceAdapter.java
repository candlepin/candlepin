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
package org.fedoraproject.candlepin.ldap;

import com.google.inject.Inject;
import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPEntry;
import com.novell.ldap.LDAPException;
import java.util.ArrayList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerPermission;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

/**
 * An LDAP based user service
 */
public class LDAPUserServiceAdapter implements UserServiceAdapter {


    private static Logger log = Logger.getLogger(LDAPUserServiceAdapter.class);
    protected String base = null;
    protected int ldapVersion = LDAPConnection.LDAP_V3;
    protected int ldapPort = 0;    
    protected String ldapServer = null;
    
    @Inject
    public LDAPUserServiceAdapter(Config config) {
        base = config.getString("ldap.base", "dc=example,dc=com");
        ldapPort = config.getInt("ldap.port");
        ldapServer = config.getString("ldap.host", "example.com");              
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
        try {
            log.debug("In the ldap adapter");
            String dn = getDN(username);
            LDAPConnection lc = getConnection();
            lc.bind(ldapVersion, dn, password.getBytes());
            return true;
        } 
        catch (LDAPException e) {
            log.debug(e.getMessage());
            return false;
        }
    }

    @Override
    public List<Role> getRoles(String username) {
        List<Role> roles = new ArrayList<Role>();
        Set<User> users = new HashSet<User>();
        users.add(new User(username, null));

        try {
            String dn = getDN(username);
            LDAPConnection lc = getConnection();
            LDAPEntry entry = lc.read(dn);
            String orgName = entry.getAttribute("ou").getStringValue();

            Set<OwnerPermission> permissions = new HashSet<OwnerPermission>();
            permissions.add(new OwnerPermission(new Owner(orgName), Access.ALL));

            // not persisting this, so I think it is ok to give it a dummy name
            roles.add(new Role("ldap", users, permissions));
        }
        catch (LDAPException e) {
            //eat it
        }

        return roles;
    }

    @Override
    public boolean isReadyOnly() {
        // We only read from LDAP
        return true;
    }

    @Override
    public User createUser(User user) {
        return user;
        //throw new UnsupportedOperationException(
        //    "This implementation does not support creating new Users!");
    }

    @Override
    public void deleteUser(User user) {
        //throw new UnsupportedOperationException(
        //    "This implementation does not support deleting Users!");
        
    }

    @Override
    public List<User> listByOwner(Owner owner) {
        throw new UnsupportedOperationException(
            "This implementation does not support deleting Users!");
    }

    @Override
    public User findByLogin(String username) {
        
        User user = null;
        try {
            String dn = getDN(username);
            LDAPConnection lc = new LDAPConnection();
            lc.connect(ldapServer, ldapPort);
            lc.read(dn);
            user = new User(username, null);

            for (Role role : getRoles(username)) {
                role.addUser(user);
            }
        } 
        catch (LDAPException e) {
            //eat it
            // TODO: don't eat it... this will bite someone eventually :)
        }        
        
        return user;
    }
    
    protected LDAPConnection getConnection() throws LDAPException {
        LDAPConnection lc = new LDAPConnection();
        lc.connect(ldapServer, ldapPort);
        return lc;
    }
    
    protected String getDN(String username) {
        return String.format("uid=%s,%s", username, base);
    }

}
