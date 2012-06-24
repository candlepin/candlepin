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
package org.candlepin.sync;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventSink;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionCurator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;

/**
 * EntitlementImporterTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EntitlementImporterTest {

    @Mock private EventSink sink;
    @Mock private SubscriptionCurator curator;
    private Owner owner;
    private Subscription testSub;
    private EntitlementImporter importer;
    private I18n i18n;


    @Before
    public void init() {
        this.testSub = new Subscription();
        this.testSub.setProduct(new Product("test-prod", "Test Prod"));
        this.testSub.setUpstreamPoolId("1");

        this.owner = new Owner();
        this.testSub.setOwner(this.owner);

        this.importer = new EntitlementImporter(this.curator, null, this.sink, i18n);
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    @Test
    public void testSingleSubscriptionInListNotInDbCausesSave() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>());

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub);
            }
        });

        // then
        verify(curator).create(testSub);
        verify(curator, never()).delete(testSub);
        verify(curator, never()).merge(testSub);
        verify(sink, atLeastOnce()).emitSubscriptionCreated(testSub);
    }

    @Test
    public void testSingleSubscriptionInDbAndListCausesMerge() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub);
            }
        });

        // then
        verify(curator, never()).create(testSub);
        verify(curator).merge(testSub);
        verify(curator, never()).delete(testSub);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub, testSub);
    }

    @Test
    public void testEmptyListCausesDbRemove() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>());

        // then
        verify(curator, never()).create(testSub);
        verify(curator, never()).merge(testSub);
        verify(curator).delete(testSub);
        verify(sink, atLeastOnce()).createSubscriptionDeleted(testSub);
        verify(sink, atLeastOnce()).sendEvent(any(Event.class));

    }
}
