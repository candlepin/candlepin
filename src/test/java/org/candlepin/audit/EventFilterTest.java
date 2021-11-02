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
package org.candlepin.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;

import org.junit.jupiter.api.Test;

import java.util.Arrays;


public class EventFilterTest {

    @Test
    public void disabledShouldNotFilter() {
        Event event1 = createEvent(Type.CREATED, Target.ENTITLEMENT);
        Event event2 = createEvent(Type.MODIFIED, Target.CONSUMER);
        EventFilter filter = new EventFilter(configurationAuditDisabled());

        assertFalse(filter.shouldFilter(event1));
        assertFalse(filter.shouldFilter(event2));
    }

    @Test
    public void filterUnknownEventPolicyDoFilter() {
        Event event = createEvent(Type.MODIFIED, Target.CONSUMER);
        EventFilter filter = new EventFilter(configurationAuditEnabled());

        assertTrue(filter.shouldFilter(event));
    }

    @Test
    public void notFilterIncludes() {
        Event event = createEvent(Type.CREATED, Target.ENTITLEMENT);
        EventFilter filter = new EventFilter(configurationAuditEnabled());

        assertFalse(filter.shouldFilter(event));
    }

    @Test
    public void policyDoNotFilterShouldNotFilter() {
        Event event1 = createEvent(Type.CREATED, Target.ENTITLEMENT);
        Event event2 = createEvent(Type.MODIFIED, Target.CONSUMER);
        EventFilter filter = new EventFilter(configurationDoNotFilter());

        assertFalse(filter.shouldFilter(event1));
        assertFalse(filter.shouldFilter(event2));
    }

    @Test
    public void policyDoNotFilterShouldFilterExcludes() {
        Event event = createEvent(Type.MODIFIED, Target.EXPORT);
        EventFilter filter = new EventFilter(configurationDoNotFilterWithExcludes());

        assertTrue(filter.shouldFilter(event));
    }

    private Event createEvent(Type type, Target target) {
        Event event = new Event();
        event.setType(type);
        event.setTarget(target);
        return event;
    }

    private Configuration configurationAuditDisabled() {
        Configuration configuration = mock(Configuration.class);

        when(configuration.getBoolean(eq(ConfigProperties.AUDIT_FILTER_ENABLED))).thenReturn(false);
        when(configuration.getString(eq(ConfigProperties.AUDIT_FILTER_DEFAULT_POLICY)))
            .thenReturn("DO_FILTER");

        return configuration;
    }

    private Configuration configurationAuditEnabled() {
        Configuration configuration = mock(Configuration.class);

        when(configuration.getBoolean(eq(ConfigProperties.AUDIT_FILTER_ENABLED))).thenReturn(true);
        when(configuration.getList(eq(ConfigProperties.AUDIT_FILTER_DO_NOT_FILTER))).thenReturn(Arrays.asList(
            "CREATED-ENTITLEMENT",
            "DELETED-ENTITLEMENT",
            "CREATED-POOL",
            "DELETED-POOL",
            "CREATED-COMPLIANCE"));
        when(configuration.getString(eq(ConfigProperties.AUDIT_FILTER_DEFAULT_POLICY)))
            .thenReturn("DO_FILTER");

        return configuration;
    }

    private Configuration configurationDoNotFilter() {
        Configuration configuration = mock(Configuration.class);

        when(configuration.getBoolean(eq(ConfigProperties.AUDIT_FILTER_ENABLED))).thenReturn(true);
        when(configuration.getList(eq(ConfigProperties.AUDIT_FILTER_DO_NOT_FILTER))).thenReturn(Arrays.asList(
            "CREATED-ENTITLEMENT",
            "DELETED-ENTITLEMENT",
            "CREATED-POOL",
            "DELETED-POOL",
            "CREATED-COMPLIANCE"));
        when(configuration.getString(eq(ConfigProperties.AUDIT_FILTER_DEFAULT_POLICY)))
            .thenReturn("DO_NOT_FILTER");

        return configuration;
    }

    private Configuration configurationDoNotFilterWithExcludes() {
        Configuration configuration = mock(Configuration.class);

        when(configuration.getBoolean(eq(ConfigProperties.AUDIT_FILTER_ENABLED))).thenReturn(true);
        when(configuration.getList(eq(ConfigProperties.AUDIT_FILTER_DO_FILTER)))
            .thenReturn(Arrays.asList("MODIFIED-EXPORT"));
        when(configuration.getString(eq(ConfigProperties.AUDIT_FILTER_DEFAULT_POLICY)))
            .thenReturn("DO_NOT_FILTER");

        return configuration;
    }

}
