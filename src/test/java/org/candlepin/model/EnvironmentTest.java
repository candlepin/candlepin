/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;

public class EnvironmentTest {

    @Test
    public void testGetOwnerKey() {
        String ownerKey = TestUtil.randomString();
        Owner owner = new Owner();
        owner.setId(TestUtil.randomString());
        owner.setKey(ownerKey);
        Environment environment = new Environment();
        environment.setOwner(owner);

        assertEquals(ownerKey, environment.getOwnerKey());
    }

    @Test
    public void testGetOwnerKeyWithNoOwner() {
        Environment environment = new Environment();

        assertNull(environment.getOwnerKey());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "normalPrefix", "abc", "TEST_123"})
    void testSetContentPrefixValidValues(String prefix) {
        Environment environment = new Environment();

        environment.setContentPrefix(prefix);

        assertEquals(prefix, environment.getContentPrefix());
    }

    @Test
    void testSetContentPrefixMaxLength() {
        Environment environment = new Environment();
        String maxLengthPrefix = "a".repeat(Environment.CONTENT_PREFIX_MAX_LENGTH);

        environment.setContentPrefix(maxLengthPrefix);

        assertEquals(maxLengthPrefix, environment.getContentPrefix());
    }

    @Test
    void testSetContentPrefixTooLong() {
        Environment environment = new Environment();
        String tooLongPrefix = "b".repeat(Environment.CONTENT_PREFIX_MAX_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> environment.setContentPrefix(tooLongPrefix));
    }

    @Test
    public void testSetContentPrefixChangesLastContentUpdate() {
        Environment environment = new Environment();
        Date before = minusTwoSeconds();
        environment.setLastContentUpdate(before);

        environment.setContentPrefix("newPrefix");
        Date after = environment.getLastContentUpdate();

        assertTrue(after.after(before));
    }

    @Test
    public void testSetLastContentUpdate() {
        Environment environment = new Environment();
        Date date = minusTwoSeconds();

        environment.setLastContentUpdate(date);

        assertEquals(date, environment.getLastContentUpdate());
    }

    @Test
    public void testSetLastContentUpdateThrowsExceptionOnNullInput() {
        Environment environment = new Environment();

        assertThrows(IllegalArgumentException.class, () -> environment.setLastContentUpdate(null));
    }

    @Test
    public void testGetLastContentUpdate() {
        Environment environment = new Environment();

        assertNotNull(environment.getLastContentUpdate());
    }

    @Test
    public void testSyncLastContentUpdate() {
        Environment environment = new Environment();
        Date before = minusTwoSeconds();
        environment.setLastContentUpdate(before);

        environment.syncLastContentUpdate();

        assertTrue(before.before(environment.getLastContentUpdate()));
    }

    private Date minusTwoSeconds() {
        return new Date(System.currentTimeMillis() - 2000);
    }

}
