package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RhsmApiCompatCuratorTest extends DatabaseTestFixture {

    @ParameterizedTest
    @NullAndEmptySource
    public void testListConsumersWithNullAndEmptyOwnerId(String ownerId) {
        List<Consumer> actual = this.rhsmApiCompatCurator.listConsumers(ownerId, null, null);

        assertThat(actual)
            .isNotNull()
            .isEmpty();;
    }

    @Test
    public void testListConsumers() {
        Owner owner = this.createOwner();
        String ownerId = owner.getId();

        // TODO: These are not populating the lastCheckin
        List<Consumer> expected = new ArrayList<>();
        expected.add(this.createConsumer(owner));
        expected.add(this.createConsumer(owner));
        expected.add(this.createConsumer(owner));
        expected.add(this.createConsumer(owner));
        expected.add(this.createConsumer(owner));

        expected.sort(Comparator.comparing(Consumer::getId)
            .thenComparing(Comparator.comparing(Consumer::getLastCheckin)));

        List<Consumer> actual = this.rhsmApiCompatCurator.listConsumers(ownerId, null, null);

        assertThat(actual)
            .isNotNull()
            .containsExactlyElementsOf(expected);
    }

    @Test
    public void testListConsumersWithAfterId() {
        Owner owner = this.createOwner();
        String ownerId = owner.getId();

        // TODO: These are not populating the lastCheckin
        List<Consumer> expected = new ArrayList<>();
        expected.add(this.createConsumer(owner));
        expected.add(this.createConsumer(owner));
        expected.add(this.createConsumer(owner));
        expected.add(this.createConsumer(owner));
        expected.add(this.createConsumer(owner));

        expected.sort(Comparator.comparing(Consumer::getId)
            .thenComparing(Comparator.comparing(Consumer::getLastCheckin)));

        int afterIndex = 2;
        String afterId = expected.get(2).getId();
        List<Consumer> actual = this.rhsmApiCompatCurator.listConsumers(ownerId, afterId, null);

        assertThat(actual)
            .isNotNull()
            .containsExactlyElementsOf(expected.subList(afterIndex, expected.size() - 1));
    }

    @Test
    public void testListConsumersWithAfterCheckin() {

    }

}
