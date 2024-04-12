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

import org.junit.jupiter.api.Test;



/**
 * Test suite for the EnvironmentContentOverride class
 */
public class EnvironmentContentOverrideTest extends ContentOverrideTest<EnvironmentContentOverride> {

    /**
     * @{inheritDoc}
     */
    @Override
    protected EnvironmentContentOverride getTestInstance() {
        return new EnvironmentContentOverride();
    }

    @Test
    public void testGetSetEnvironment() {
        Environment environment = new Environment();
        EnvironmentContentOverride override = this.getTestInstance();

        // The getParent method is synonymous with fetching the environment for EnvContentOverrides
        assertNull(override.getEnvironment());
        assertNull(override.getParent());

        EnvironmentContentOverride output = override.setEnvironment(environment);
        assertSame(override, output);

        assertEquals(environment, output.getEnvironment());
        assertEquals(environment, output.getParent());
    }

    @Test
    public void testGetSetNullEnvironment() {
        EnvironmentContentOverride override = this.getTestInstance();

        // The getParent method is synonymous with fetching the environment for EnvContentOverrides
        assertNull(override.getEnvironment());
        assertNull(override.getParent());

        EnvironmentContentOverride output = override.setEnvironment(null);
        assertSame(override, output);

        assertNull(output.getEnvironment());
        assertNull(output.getParent());
    }

}
