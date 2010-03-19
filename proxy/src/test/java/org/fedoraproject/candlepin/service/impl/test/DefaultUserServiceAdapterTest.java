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
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.service.impl.DefaultUserServiceAdapter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class DefaultUserServiceAdapterTest {

    private UserPasswordConfig config;

    @Before
    public void init() {
        this.config = new UserPasswordConfig();
    }

    @Test
    public void noPasswordsDefined() {
        Assert.assertTrue(validate("foo", "bar"));
    }

    @Test
    public void noPasswordsNullPassword() {
        Assert.assertTrue(validate("foo", null));
    }

    @Test
    public void noPasswordsNullEverything() {
        Assert.assertTrue(validate(null, null));
    }

    @Test
    public void singleMatch() {
        this.config.addUserPassword("red", "hat");

        Assert.assertTrue(validate("red", "hat"));
    }

    @Test
    public void singleBadPassword() {
        this.config.addUserPassword("red", "hat");

        Assert.assertFalse(validate("red", "fedora"));
    }

    @Test
    public void singleInvalidUsername() {
        this.config.addUserPassword("red", "hat");

        Assert.assertFalse(validate("blue", "hat"));
    }

    @Test
    public void singleNullUsername() {
        this.config.addUserPassword("red", "hat");

        Assert.assertFalse(validate(null, "hat"));
    }

    @Test
    public void singleNullPasswordValidUser() {
        this.config.addUserPassword("user", "pass");

        Assert.assertFalse(validate("user", null));
    }

    @Test
    public void singleNullPasswordInvalidUser() {
        this.config.addUserPassword("user", "pass");

        Assert.assertFalse(validate("red", null));
    }

    @Test
    public void multipleMatch() {
        this.config.addUserPassword("user", "pass");
        this.config.addUserPassword("red", "hat");
        this.config.addUserPassword("great", "curve");

        Assert.assertTrue(validate("red", "hat"));
    }

    private boolean validate(String username, String password) {
        DefaultUserServiceAdapter userService = new DefaultUserServiceAdapter(this.config);

        return userService.validateUser(username, password);
    }

    private class UserPasswordConfig extends Config {

        private Map<String, String> userPasswords;

        private UserPasswordConfig() {
            userPasswords = new HashMap<String, String>();
        }

        public void addUserPassword(String username, String password) {
            userPasswords.put("auth.user." + username, password);
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
