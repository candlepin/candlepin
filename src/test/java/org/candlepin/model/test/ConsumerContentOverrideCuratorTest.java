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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Owner;
import org.candlepin.policy.js.override.OverrideRules;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ConsumerContentOverrideCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private ConsumerType ct;
    private Consumer consumer;
    private OverrideRules overrideRules;

    @Before
    public void setUp() {
        owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        consumer = new Consumer("a consumer", "username", owner, ct);
        consumerCurator.create(consumer);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void normalCreateAndRetrieve() {
        ConsumerContentOverride cco = new ConsumerContentOverride(
            consumer, "test-content", "name", "value");
        consumerContentOverrideCurator.create(cco);

        ConsumerContentOverride cco2 = consumerContentOverrideCurator.retrieve(
            consumer, "test-content", "name");
        assert (cco2 != null);
        assertEquals("value", cco2.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void normalCreateAndUpdate() {
        ConsumerContentOverride cco = new ConsumerContentOverride(
            consumer, "test-content", "name", "value");
        consumerContentOverrideCurator.create(cco);

        cco.setValue("value-update");
        consumerContentOverrideCurator.merge(cco);

        ConsumerContentOverride cco2 = consumerContentOverrideCurator.retrieve(
            consumer, "test-content", "name");
        assert (cco2 != null);
        assertEquals("value-update", cco2.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void deleteByName() {
        ConsumerContentOverride cco = new ConsumerContentOverride(
            consumer, "test-content", "name", "value");
        consumerContentOverrideCurator.create(cco);

        consumerContentOverrideCurator.removeByName(consumer, "test-content", "name");
        ConsumerContentOverride cco2 = consumerContentOverrideCurator.retrieve(
            consumer, "test-content", "name");
        assert (cco2 == null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void deleteByLabel() {
        ConsumerContentOverride cco1 = new ConsumerContentOverride(
            consumer, "test-content", "name1", "value");
        consumerContentOverrideCurator.create(cco1);
        ConsumerContentOverride cco2 = new ConsumerContentOverride(
            consumer, "test-content", "name2", "value");
        consumerContentOverrideCurator.create(cco2);

        consumerContentOverrideCurator.removeByContentLabel(consumer, "test-content");
        List<ConsumerContentOverride> ccoList = consumerContentOverrideCurator.listAll();
        assertEquals(ccoList.size(), 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void deleteByConsumer() {
        ConsumerContentOverride cco1 = new ConsumerContentOverride(
            consumer, "test-content1", "name1", "value");
        consumerContentOverrideCurator.create(cco1);
        ConsumerContentOverride cco2 = new ConsumerContentOverride(
            consumer, "test-content2", "name2", "value");
        consumerContentOverrideCurator.create(cco2);

        consumerContentOverrideCurator.removeByConsumer(consumer);
        List<ConsumerContentOverride> ccoList = consumerContentOverrideCurator.listAll();
        assertEquals(ccoList.size(), 0);
    }
}
