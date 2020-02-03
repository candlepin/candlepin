/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractDTOTest;
import org.candlepin.util.Util;

import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;



/**
 * Test suite for the UserDTO class
 */
public class UserDTOTest extends AbstractDTOTest<UserDTO> {

    protected Map<String, Object> values;

    public UserDTOTest() {
        super(UserDTO.class);

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Username", "test-username");
        this.values.put("HashedPassword", "test-password-hash");
        this.values.put("SuperAdmin", true);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }

    @Test
    public void testPasswordHashing() {
        UserDTO dto = new UserDTO();
        assertNull(dto.getHashedPassword());

        String password = "my_password";
        String hashed = Util.hash(password);

        // This should result in a hashed password
        dto.setPassword(password);

        assertEquals(hashed, dto.getHashedPassword());
    }

    @Test
    public void testPasswordReset() {
        UserDTO dto = new UserDTO();
        assertNull(dto.getHashedPassword());

        String password = "my_password";

        dto.setPassword(password);
        assertNotNull(dto.getHashedPassword());

        dto.setPassword(null);
        assertNull(dto.getHashedPassword());
    }
}
