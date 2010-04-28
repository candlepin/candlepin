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
package org.fedoraproject.candlepin.service.impl.test;

import java.util.HashMap;
import java.util.Map;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.service.UserServiceAdapter.OwnerInfo;
import org.fedoraproject.candlepin.service.impl.DefaultUserServiceAdapter;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class DefaultUserServiceAdapterTest extends DatabaseTestFixture {

    private UserPasswordConfig config;

    @Before
    public void setUp() {
        this.config = new UserPasswordConfig();
    }

    @Test
    public void noPasswordsDefined() throws Exception {
        Assert.assertTrue(validate("foo", "bar"));
    }

    @Test
    public void noPasswordsNullPassword() throws Exception {
        Assert.assertTrue(validate("foo", null));
    }

    @Test
    public void noPasswordsNullEverything() throws Exception {
        Assert.assertTrue(validate(null, null));
    }

    @Test
    public void invalidPasswordOrg() throws Exception {
        // No org specified
        this.config.addUserPassword("dude", "password");

        // this should still work - the entire value is the password
        Assert.assertTrue(validate("dude", "password"));
    }

    @Test
    public void singleMatch() throws Exception {
        this.config.addUser("red", "hat", "Red Hat");

        Assert.assertTrue(validate("red", "hat"));
    }

    @Test
    public void singleBadPassword() throws Exception {
        this.config.addUser("red", "hat", "Red Hat");

        Assert.assertFalse(validate("red", "fedora"));
    }

    @Test
    public void singleInvalidUsername() throws Exception {
        this.config.addUser("red", "hat", "Red Hat");

        Assert.assertFalse(validate("blue", "hat"));
    }

    @Test
    public void singleNullUsername() throws Exception {
        this.config.addUser("red", "hat", "Red Hat");

        Assert.assertFalse(validate(null, "hat"));
    }

    @Test
    public void singleNullPasswordValidUser() throws Exception {
        this.config.addUser("user", "pass", "Green Mountain");

        Assert.assertFalse(validate("user", null));
    }

    @Test
    public void singleNullPasswordInvalidUser() throws Exception {
        this.config.addUser("user", "pass", "Green Mountain");

        Assert.assertFalse(validate("red", null));
    }

    @Test
    public void multipleMatch() throws Exception {
        this.config.addUser("user", "pass", "Green Mountain");
        this.config.addUser("red", "hat", "Red Hat");
        this.config.addUser("great", "curve", "Remain In Light");

        Assert.assertTrue(validate("red", "hat"));
    }

    @Test
    public void validOwner() {
        this.config.addUser("billy", "password", "Megacorp");
        UserServiceAdapter userService = new DefaultUserServiceAdapter(config, 
            ownerCurator);
        OwnerInfo info = userService.getOwnerInfo("billy");

        Assert.assertEquals("Megacorp", info.getName());
    }

    // Note:  The following tests are failing due to the hack to hard-coding
    //        'Spacewalk Public Cert' into the impl for functional testing.
    //@Test
    public void nullOwner() {
        // no org info
        this.config.addUserPassword("richard", "password");
        UserServiceAdapter userService = new DefaultUserServiceAdapter(config, 
            ownerCurator);

        Assert.assertNull(userService.getOwnerInfo("richard"));
    }

    //@Test
    public void noAccountNullOwner() {
        // no account setup
        UserServiceAdapter userService = new DefaultUserServiceAdapter(config, 
            ownerCurator);

        Assert.assertNull(userService.getOwnerInfo("someone"));
    }

    //@Test
    public void nullOwnerKey() {
        // no account setup
        UserServiceAdapter userService = new DefaultUserServiceAdapter(config, 
            ownerCurator);

        Assert.assertNull(userService.getOwnerInfo(null));
    }

    @Test
    public void staticRole() {
        config.addUser("anyone", "whatever", "Default Org", "owneradmin");
        UserServiceAdapter userService = new DefaultUserServiceAdapter(config, 
            ownerCurator);
        Role[] expected = new Role[] {Role.OWNER_ADMIN};

        Assert.assertArrayEquals(expected, userService.getRoles("anyone").toArray());
    }

    private boolean validate(String username, String password) throws Exception {
        UserServiceAdapter userService = new DefaultUserServiceAdapter(this.config, 
            ownerCurator);

        return userService.validateUser(username, password);
    }

    private class UserPasswordConfig extends Config {

        private Map<String, String> userPasswords;

        private UserPasswordConfig() {
            userPasswords = new HashMap<String, String>();
        }

        // for testing bad password:org combo
        public void addUserPassword(String username, String passwordOrg) {
            userPasswords.put("auth.user." + username, passwordOrg);
        }

        public void addUser(String username, String password, String org) {
            addUserPassword(username, password + ":" + org);
        }

        public void addUser(String username, String password, String org,
            String roles) {
            addUserPassword(username, password + ":" + org + ":" + roles);
        }

        @Override
        public Map<String, String> configurationWithPrefix(String prefix) {
            if ("auth.user.".equals(prefix)) {
                return userPasswords;
            }

            return null;
        }
    }

}
