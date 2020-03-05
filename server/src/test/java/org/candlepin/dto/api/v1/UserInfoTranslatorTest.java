/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.User;
import org.candlepin.service.model.UserInfo;



/**
 * Test suite for the UserInfoTranslator class
 */
public class UserInfoTranslatorTest extends AbstractTranslatorTest<UserInfo, UserDTO, UserInfoTranslator> {

    protected UserInfoTranslator translator = new UserInfoTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, UserInfo.class, UserDTO.class);
    }

    @Override
    protected UserInfoTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected UserInfo initSourceObject() {
        // Impl note: Users are UserInfo implementations
        User user = new User();

        user.setId("user_id");
        user.setUsername("user_username");
        user.setPassword("user_password");
        user.setSuperAdmin(true);

        return user;
    }

    @Override
    protected UserDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new UserDTO();
    }

    @Override
    protected void verifyOutput(UserInfo source, UserDTO dest, boolean childrenGenerated) {
        if (source != null) {
            // This DTO does not have any nested objects, so we don't need to worry about the
            // childrenGenerated flag

            assertEquals(source.getUsername(), dest.getUsername());
            assertEquals(source.isSuperAdmin(), dest.getSuperAdmin());

            // Under no circumstance should we be copying over the password field on translation.
            // This should always be null on the DTO.
            assertNull(dest.getPassword());
        }
        else {
            assertNull(dest);
        }
    }
}
