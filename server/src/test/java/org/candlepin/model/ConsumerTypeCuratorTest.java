/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.junit.Assert.*;

import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Test;



/**
 * Test suite for the ConsumerTypeCurator
 */
public class ConsumerTypeCuratorTest extends DatabaseTestFixture {


    @Test
    public void testGetConsumerType() {
        ConsumerType ctype = this.createConsumerType();
        Consumer consumer = new Consumer();

        consumer.setTypeId(ctype.getId());

        ConsumerType test = this.consumerTypeCurator.getConsumerType(consumer);
        assertSame(ctype, test);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetConsumerTypeRequiresConsumer() {
        this.consumerTypeCurator.getConsumerType(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetConsumerTypeRequiresConsumerWithType() {
        ConsumerType ctype = this.createConsumerType();
        Consumer consumer = new Consumer();

        this.consumerTypeCurator.getConsumerType(consumer);
    }

    @Test(expected = IllegalStateException.class)
    public void testGetConsumerTypeExpectsValidConsumerTypeId() {
        ConsumerType ctype = this.createConsumerType();
        Consumer consumer = new Consumer();

        consumer.setTypeId("bad-type-id");

        this.consumerTypeCurator.getConsumerType(consumer);
    }

    @Test
    public void testgetByLabel() {
        String label = "test-label";
        ConsumerType ctype = this.createConsumerType(label, false);

        ConsumerType test = this.consumerTypeCurator.getByLabel(label);
        assertSame(ctype, test);
    }

    @Test
    public void testgetByLabelForBadLabel() {
        String label = "test-label";
        ConsumerType ctype = this.createConsumerType(label, false);

        ConsumerType test = this.consumerTypeCurator.getByLabel("bad_label");
        assertNull(test);
    }

    @Test
    public void testgetByLabelExistingTypeWithCreation() {
        String label = "test-label";
        ConsumerType ctype = this.createConsumerType(label, false);

        ConsumerType test = this.consumerTypeCurator.getByLabel(label, true);
        assertSame(ctype, test);
    }

    @Test
    public void testgetByLabelNewTypeWithCreation() {
        String label = "new-ctype";
        ConsumerType ctype = this.createConsumerType("test-type", true);

        ConsumerType test = this.consumerTypeCurator.getByLabel(label, true);
        assertNotNull(test);
        assertNotSame(ctype, test);

        assertEquals(label, test.getLabel());
    }

    @Test
    public void testgetByLabelNewTypeFromEnumWithCreation() {
        ConsumerTypeEnum cte = ConsumerTypeEnum.CANDLEPIN;
        ConsumerType ctype = this.createConsumerType("test-type", true);

        ConsumerType test = this.consumerTypeCurator.getByLabel(cte.getLabel(), true);
        assertNotNull(test);
        assertNotSame(ctype, test);

        assertEquals(cte.getLabel(), test.getLabel());
        assertEquals(cte.isManifest(), test.isManifest());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testgetByLabelWithNullLabel() {
        this.consumerTypeCurator.getByLabel(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testgetByLabelWithNullLabelAndCreation() {
        this.consumerTypeCurator.getByLabel(null, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testgetByLabelWithEmptyLabel() {
        this.consumerTypeCurator.getByLabel("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testgetByLabelWithEmptyLabelAndCreation() {
        this.consumerTypeCurator.getByLabel("", true);
    }
}
