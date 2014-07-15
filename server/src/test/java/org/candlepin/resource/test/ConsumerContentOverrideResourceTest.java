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

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.Owner;
import org.candlepin.policy.js.override.OverrideRules;
import org.candlepin.resource.ConsumerContentOverrideResource;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ContentOverrideValidator;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
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
public class ConsumerContentOverrideResourceTest extends DatabaseTestFixture {

    private I18n i18n;
    private ConsumerContentOverrideResource resource;
    private Consumer consumer;

    @Mock
    private ConsumerCurator consumerCurator;

    @Mock
    private ConsumerContentOverrideCurator consumerContentOverrideCurator;

    private ContentOverrideValidator contentOverrideValidator;

    @Mock
    private OverrideRules overrideRules;

    @Mock
    private Principal principal;

    private UriInfo context;

    @Before
    public void setUp() {
        consumer = new Consumer("test-consumer", "test-user", new Owner(
            "Test Owner"), new ConsumerType("test-consumer-type-"));
        MultivaluedMap<String, String> mvm = new MultivaluedMapImpl<String, String>();
        mvm.add("consumer_uuid", consumer.getUuid());
        context = mock(UriInfo.class);
        when(context.getPathParameters()).thenReturn(mvm);

        when(consumerCurator.verifyAndLookupConsumer(
            eq(consumer.getUuid()))).thenReturn(consumer);
        when(overrideRules.canOverrideForConsumer(
            any(String.class))).thenReturn(true);
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        contentOverrideValidator = new ContentOverrideValidator(i18n, overrideRules);
        resource = new ConsumerContentOverrideResource(consumerContentOverrideCurator,
            consumerCurator, contentOverrideValidator, i18n);
        when(principal.canAccess(any(Object.class), any(SubResource.class),
            any(Access.class))).thenReturn(true);
    }

    @Test
    public void testAddOverride() {
        List<ContentOverride> entries = new LinkedList<ContentOverride>();
        ConsumerContentOverride toAdd = new ConsumerContentOverride(consumer, "label",
            "overridename", "overridevalue");
        entries.add(toAdd);
        resource.addContentOverrides(context, principal, entries);
        Mockito.verify(consumerContentOverrideCurator,
            Mockito.times(1)).addOrUpdate(consumer, toAdd);
    }
}
