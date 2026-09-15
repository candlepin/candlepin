/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;



/**
 * Test suite for the ConsumerTypeCurator
 */
public class ConsumerTypeCuratorTest extends DatabaseTestFixture {
    @ParameterizedTest
    @ValueSource(strings = {"Y", "y", "N", "n"})
    public void testManifestPredicatesHandleStoredFlagCase(String storedFlag) {
        boolean manifest = "y".equalsIgnoreCase(storedFlag);
        ConsumerType type = this.createConsumerType("case-test", manifest);
        this.getEntityManager().flush();
        // Bypass the converter to reproduce values inserted by database migrations.
        this.getEntityManager().createNativeQuery("UPDATE cp_consumer_type SET manifest = :flag " +
            "WHERE id = :id", Integer.class)
            .setParameter("flag", storedFlag)
            .setParameter("id", type.getId())
            .executeUpdate();
        this.getEntityManager().clear();

        assertThat(this.consumerTypeCurator.get(type.getId()).isManifest()).isEqualTo(manifest);
        assertThat(this.getEntityManager().createQuery("SELECT t.id FROM ConsumerType t " +
            "WHERE t.id = :id AND t.manifest = :manifest", String.class)
            .setParameter("id", type.getId())
            .setParameter("manifest", manifest)
            .getResultList()).containsExactly(type.getId());
        assertThat(this.getEntityManager().createQuery("SELECT t.id FROM ConsumerType t " +
            "WHERE t.id = :id AND t.manifest = :manifest", String.class)
            .setParameter("id", type.getId())
            .setParameter("manifest", !manifest)
            .getResultList()).isEmpty();
    }

    @Test
    public void testGetConsumerType() {
        ConsumerType ctype = this.createConsumerType();
        Consumer consumer = new Consumer();

        consumer.setTypeId(ctype.getId());

        ConsumerType test = this.consumerTypeCurator.getConsumerType(consumer);
        assertSame(ctype, test);
    }

    @Test
    public void testGetConsumerTypeRequiresConsumer() {
        assertThrows(IllegalArgumentException.class, () -> consumerTypeCurator.getConsumerType(null));
    }

    @Test
    public void testGetConsumerTypeRequiresConsumerWithType() {
        ConsumerType ctype = this.createConsumerType();
        Consumer consumer = new Consumer();

        assertThrows(IllegalArgumentException.class, () -> consumerTypeCurator.getConsumerType(consumer));
    }

    @Test
    public void testGetConsumerTypeExpectsValidConsumerTypeId() {
        ConsumerType ctype = this.createConsumerType();
        Consumer consumer = new Consumer();

        consumer.setTypeId("bad-type-id");

        assertThrows(IllegalStateException.class, () -> consumerTypeCurator.getConsumerType(consumer));
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

    @Test
    public void testgetByLabelWithNullLabel() {
        assertThrows(IllegalArgumentException.class, () -> consumerTypeCurator.getByLabel(null));
    }

    @Test
    public void testgetByLabelWithNullLabelAndCreation() {
        assertThrows(IllegalArgumentException.class, () -> consumerTypeCurator.getByLabel(null, true));
    }

    @Test
    public void testgetByLabelWithEmptyLabel() {
        assertThrows(IllegalArgumentException.class, () -> consumerTypeCurator.getByLabel(""));
    }

    @Test
    public void testgetByLabelWithEmptyLabelAndCreation() {
        assertThrows(IllegalArgumentException.class, () -> consumerTypeCurator.getByLabel("", true));
    }
}
