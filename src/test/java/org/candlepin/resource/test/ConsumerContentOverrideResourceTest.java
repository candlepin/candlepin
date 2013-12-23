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
package org.candlepin.resource.test;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.policy.js.override.OverrideRules;
import org.candlepin.resource.ConsumerContentOverrideResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * ConsumerContentOverrideResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ConsumerContentOverrideResourceTest {

    private I18n i18n;
    private ConsumerContentOverrideResource resource;
    private Consumer consumer;

    @Mock
    private ConsumerCurator consumerCurator;

    @Mock
    private ConsumerContentOverrideCurator consumerContentOverrideCurator;

    @Mock
    private OverrideRules overrideRules;

    @Before
    public void setUp() {
        consumer = new Consumer("test-consumer", "test-user", new Owner(
            "Test Owner"), new ConsumerType("test-consumer-type-"));
        when(consumerCurator.verifyAndLookupConsumer(
            eq(consumer.getUuid()))).thenReturn(consumer);
        when(overrideRules.canOverrideForConsumer(any(Consumer.class),
            any(String.class))).thenReturn(true);
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        resource = new ConsumerContentOverrideResource(consumerContentOverrideCurator,
            consumerCurator, overrideRules, i18n);
    }

    @Test
    public void testAddOverride() {
        List<ConsumerContentOverride> entries = new LinkedList<ConsumerContentOverride>();
        ConsumerContentOverride toAdd = new ConsumerContentOverride(consumer, "label",
            "overridename", "overridevalue");
        entries.add(toAdd);
        resource.addContentOverrides(consumer.getUuid(), entries);
        Mockito.verify(consumerContentOverrideCurator, Mockito.times(1)).create(toAdd);
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideLongName() {
        List<ConsumerContentOverride> entries = new LinkedList<ConsumerContentOverride>();
        ConsumerContentOverride toAdd = new ConsumerContentOverride(consumer, "label",
            buildLongString(), "overridevalue");
        entries.add(toAdd);
        resource.addContentOverrides(consumer.getUuid(), entries);
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideLongValue() {
        List<ConsumerContentOverride> entries = new LinkedList<ConsumerContentOverride>();
        ConsumerContentOverride toAdd = new ConsumerContentOverride(consumer, "label",
            "overridename", buildLongString());
        entries.add(toAdd);
        resource.addContentOverrides(consumer.getUuid(), entries);
    }

    private String buildLongString() {
        String result = "test";
        while (result.length() < 256) {
            result += result;
        }
        return result;
    }
}
