/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.User;
import org.candlepin.util.Util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Test suite for the UserDTOTranslator class.
 */
public class UserDTOTranslatorTest extends AbstractTranslatorTest<UserDTO, User, UserDTOTranslator> {

    protected UserDTOTranslator translator = new UserDTOTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, UserDTO.class, User.class);
    }

    @Override
    protected UserDTOTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected UserDTO initSourceObject() {
        UserDTO userDTO = new UserDTO()
            .username("user_username")
            .password("user_password")
            .superAdmin(true);

        return userDTO;
    }

    @Override
    protected User initDestinationObject() {
        return new User();
    }

    @Override
    protected void verifyOutput(UserDTO source, User dest, boolean childrenGenerated) {
        if (source != null) {
            // This DTO does not have any nested objects, so we don't need to worry about the
            // childrenGenerated flag

            assertEquals(source.getUsername(), dest.getUsername());
            assertEquals(source.getSuperAdmin(), dest.isSuperAdmin());

            assertEquals(Util.hash(source.getPassword()), dest.getHashedPassword());
        }
        else {
            assertNull(dest);
        }
    }
}
