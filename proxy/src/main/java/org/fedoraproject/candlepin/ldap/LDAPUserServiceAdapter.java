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

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.NewRole;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Permission;
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
    //FIXME this seems hacky
    public List<Owner> getOwners(String username) {
        List<Owner> owners = new ArrayList<Owner>();
        try {
            String dn = getDN(username);
            LDAPConnection lc = getConnection();
            LDAPEntry entry = lc.read(dn);
            String orgName = entry.getAttribute("ou").getStringValue();
            owners.add(new Owner(orgName));
        } 
        catch (LDAPException e) {
            //eat it
        }        
        
        return owners;
    }

    @Override
    // FIXME This is hacky
    public List<Role> getRoles(String username) {
        List<Role> roles = new LinkedList<Role>();
        roles.add(Role.SUPER_ADMIN);
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

            for (Owner owner : getOwners(username)) {
                Permission p = new Permission(owner, EnumSet.of(Role.OWNER_ADMIN));
                NewRole r = new NewRole();
                r.addPermission(p);
                r.addUser(user);
            }
        } 
        catch (LDAPException e) {
            //eat it
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
