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
import static org.junit.jupiter.api.Assertions.assertSame;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;

import org.junit.jupiter.api.Test;



/**
 * Test suite for the ActivationKeyContentOverride class
 */
public class ActivationKeyContentOverrideTest extends ContentOverrideTest<ActivationKeyContentOverride> {

    /**
     * @{inheritDoc}
     */
    @Override
    protected ActivationKeyContentOverride getTestInstance() {
        return new ActivationKeyContentOverride();
    }

    @Test
    public void testGetSetKey() {
        ActivationKey key = new ActivationKey();
        ActivationKeyContentOverride override = this.getTestInstance();

        // The getParent method is synonymous with fetching the key for AKContentOverrides
        assertNull(override.getKey());
        assertNull(override.getParent());

        ActivationKeyContentOverride output = override.setKey(key);
        assertSame(override, output);

        assertEquals(key, output.getKey());
        assertEquals(key, output.getParent());
    }

    @Test
    public void testGetSetNullKey() {
        ActivationKeyContentOverride override = this.getTestInstance();

        // The getParent method is synonymous with fetching the key for AKContentOverrides
        assertNull(override.getKey());
        assertNull(override.getParent());

        ActivationKeyContentOverride output = override.setKey(null);
        assertSame(override, output);

        assertNull(output.getKey());
        assertNull(output.getParent());
    }

    @Test
    public void testGetSetParent() {
        ActivationKey key = new ActivationKey();
        ActivationKeyContentOverride override = this.getTestInstance();

        // The getParent method is synonymous with fetching the key for AKContentOverrides
        assertNull(override.getParent());
        assertNull(override.getKey());

        ActivationKeyContentOverride output = override.setParent(key);
        assertSame(override, output);

        assertEquals(key, output.getParent());
        assertEquals(key, output.getKey());
    }

    @Test
    public void testGetSetNullParent() {
        ActivationKeyContentOverride override = this.getTestInstance();

        // The getParent method is synonymous with fetching the key for AKContentOverrides
        assertNull(override.getParent());
        assertNull(override.getKey());

        ActivationKeyContentOverride output = override.setParent(null);
        assertSame(override, output);

        assertNull(output.getParent());
        assertNull(output.getKey());
    }

    @Test
    public void testBuildConsumerContentOverride() {
        String name = "test_name";
        String label = "test_label";
        String value = "test_value";

        Consumer consumer = new Consumer();

        ActivationKeyContentOverride akco = new ActivationKeyContentOverride()
            .setId("test_id")
            .setName(name)
            .setContentLabel(label)
            .setValue(value);

        ConsumerContentOverride cco = akco.buildConsumerContentOverride(consumer);

        assertNotNull(cco);
        assertEquals(consumer, cco.getConsumer());
        assertEquals(consumer, cco.getParent());
        assertNull(cco.getId());
        assertEquals(name, cco.getName());
        assertEquals(label, cco.getContentLabel());
        assertEquals(value, cco.getValue());

        // Munge the data in the original AKCO to verify the generated CCO is not affected
        akco.setId("updated_id")
            .setName("updated_name")
            .setContentLabel("updated_label")
            .setValue("updated_value");

        assertNull(cco.getId());
        assertEquals(name, cco.getName());
        assertEquals(label, cco.getContentLabel());
        assertEquals(value, cco.getValue());
    }
}
