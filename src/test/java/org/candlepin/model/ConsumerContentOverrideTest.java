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
 * Test suite for the ConsumerContentOverride class
 */
public class ConsumerContentOverrideTest extends ContentOverrideTest<ConsumerContentOverride> {

    /**
     * @{inheritDoc}
     */
    @Override
    protected ConsumerContentOverride getTestInstance() {
        return new ConsumerContentOverride();
    }

    @Test
    public void testGetSetConsumer() {
        Consumer consumer = new Consumer();
        ConsumerContentOverride override = this.getTestInstance();

        // The getParent method is synonymous with fetching the consumer for EnvContentOverrides
        assertNull(override.getConsumer());
        assertNull(override.getParent());

        ConsumerContentOverride output = override.setConsumer(consumer);
        assertSame(override, output);

        assertEquals(consumer, output.getConsumer());
        assertEquals(consumer, output.getParent());
    }

    @Test
    public void testGetSetNullConsumer() {
        ConsumerContentOverride override = this.getTestInstance();

        // The getParent method is synonymous with fetching the consumer for EnvContentOverrides
        assertNull(override.getConsumer());
        assertNull(override.getParent());

        ConsumerContentOverride output = override.setConsumer(null);
        assertSame(override, output);

        assertNull(output.getConsumer());
        assertNull(output.getParent());
    }

}
