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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class EventFilterTest {

    private EventFilter eventFilterEnabled = null;
    private EventFilter eventFilterDisabled = null;
    private EventFilter eventFilterDoNotFilter = null;

    private Event event1;
    private Event event2;
    private Event event3;

    @Mock
    private Configuration configurationAuditEnabled;

    @Mock
    private Configuration configurationAuditDisabled;

    @Mock
    private Configuration configurationDoNotFilter;

    @Before
    @SuppressWarnings("checkstyle:indentation")
    public void init() throws Exception {
        when(configurationAuditEnabled
                .getBoolean(eq(ConfigProperties.AUDIT_FILTER_ENABLED))).thenReturn(true);
        when(configurationAuditEnabled
            .getList(eq(ConfigProperties.AUDIT_FILTER_DO_NOT_FILTER))).thenReturn(
                Arrays.asList("CREATED-ENTITLEMENT",
                "DELETED-ENTITLEMENT",
                "CREATED-POOL",
                "DELETED-POOL",
                "CREATED-COMPLIANCE"));
        when(configurationAuditEnabled
                .getString(eq(ConfigProperties.AUDIT_FILTER_DEFAULT_POLICY))).thenReturn("DO_FILTER");

        when(configurationDoNotFilter
                .getBoolean(eq(ConfigProperties.AUDIT_FILTER_ENABLED))).thenReturn(true);
        when(configurationDoNotFilter
            .getList(eq(ConfigProperties.AUDIT_FILTER_DO_NOT_FILTER))).thenReturn(
                Arrays.asList("CREATED-ENTITLEMENT",
                "DELETED-ENTITLEMENT",
                "CREATED-POOL",
                "DELETED-POOL",
                "CREATED-COMPLIANCE"));
        when(configurationDoNotFilter
            .getList(eq(ConfigProperties.AUDIT_FILTER_DO_FILTER))).thenReturn(
                Arrays.asList("MODIFIED-EXPORT"));
        when(configurationDoNotFilter
                .getString(eq(ConfigProperties.AUDIT_FILTER_DEFAULT_POLICY))).thenReturn("DO_NOT_FILTER");

        when(configurationAuditDisabled
                .getBoolean(eq(ConfigProperties.AUDIT_FILTER_ENABLED))).thenReturn(false);
        when(configurationAuditDisabled
                .getString(eq(ConfigProperties.AUDIT_FILTER_DEFAULT_POLICY))).thenReturn("DO_FILTER");


        eventFilterEnabled = new EventFilter(configurationAuditEnabled);
        eventFilterDisabled = new EventFilter(configurationAuditDisabled);
        eventFilterDoNotFilter = new EventFilter(configurationDoNotFilter);

        event1 = new Event();
        event1.setType(Type.CREATED);
        event1.setTarget(Target.ENTITLEMENT);

        event2 = new Event();
        event2.setType(Type.MODIFIED);
        event2.setTarget(Target.CONSUMER);

        event3 = new Event();
        event3.setType(Type.MODIFIED);
        event3.setTarget(Target.EXPORT);

    }

    @Test
    public void disabledShouldntFilter() {
        assertFalse(eventFilterDisabled.shouldFilter(event1));
        assertFalse(eventFilterDisabled.shouldFilter(event2));
    }


    @Test
    public void filterUnknownEventPolicyDoFilter() {
        assertTrue(eventFilterEnabled.shouldFilter(event2));
    }

    @Test
    public void notFilterIncludes() {
        assertFalse(eventFilterEnabled.shouldFilter(event1));
    }


    @Test
    public void policyDoNotFilterShouldntFilter() {
        assertFalse(eventFilterDoNotFilter.shouldFilter(event1));
        assertFalse(eventFilterDoNotFilter.shouldFilter(event2));
    }

    @Test
    public void policyDoNotFilterShouldFilterExcludes() {
        assertTrue(eventFilterDoNotFilter.shouldFilter(event3));
    }

}
