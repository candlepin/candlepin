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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;



/**
 * Test suite for the ContentOverride tree of objects
 */
public abstract class ContentOverrideTest<T extends ContentOverride> {

    /**
     * Builds and returns a minimally initialized ContentOverride for testing
     */
    protected abstract T getTestInstance();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "test_value" })
    public void testGetSetID(String value) {
        T override = this.getTestInstance();

        assertNull(override.getId());

        T output = (T) override.setId(value);
        assertSame(override, output);

        assertEquals(value, output.getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "test_value" })
    public void testGetSetContentLabel(String value) {
        T override = this.getTestInstance();

        assertNull(override.getContentLabel());

        T output = (T) override.setContentLabel(value);
        assertSame(override, output);

        assertEquals(value, output.getContentLabel());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "test_value" })
    public void testGetSetName(String value) {
        T override = this.getTestInstance();

        assertNull(override.getName());

        T output = (T) override.setName(value);
        assertSame(override, output);

        assertEquals(value, output.getName());
    }

    @ParameterizedTest
    @ValueSource(strings = { "test_value-1", "TEST-VALUE-2", "TeSt_VaLuE-3" })
    public void testNameAlwaysStoredAsLowerCase(String value) {
        T override = this.getTestInstance();

        assertNull(override.getName());

        T output = (T) override.setName(value);
        assertSame(override, output);

        String expected = value.toLowerCase();
        assertEquals(expected, override.getName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "test_value", "TeSt_ValUe-2" })
    public void testGetSetValue(String value) {
        T override = this.getTestInstance();

        assertNull(override.getValue());

        T output = (T) override.setValue(value);
        assertSame(override, output);

        assertEquals(value, output.getValue());
    }

}
