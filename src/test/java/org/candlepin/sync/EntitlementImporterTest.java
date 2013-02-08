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
    private Subscription testSub1;
    private Subscription testSub2;
    private Subscription testSub3;
    private Subscription testSub4;
    private Subscription testSub5;
    private Subscription testSub6;
    private Subscription testSub7;
    private Subscription testSub8;
    private Subscription testSub9;
    private Subscription testSub10;
    private Subscription testSub11;
    private Subscription testSub12;
    private Subscription testSub13;
    private Subscription testSub14;
    private EntitlementImporter importer;
    private I18n i18n;
    private int index = 1;


    @Before
    public void init() {
        this.owner = new Owner();
        this.testSub1 = createSubscription(owner, "test-prod-1", "up1", "ue1", "uc1", 25);
        this.testSub2 = createSubscription(owner, "test-prod-1", "up1", "ue2", "uc1", 20);
        this.testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        this.testSub4 = createSubscription(owner, "test-prod-1", "up1", "ue4", "uc1", 10);
        this.testSub5 = createSubscription(owner, "test-prod-1", "up1", "ue5", "uc1", 5);
        this.testSub6 = createSubscription(owner, "test-prod-1", "up1", "ue6", "uc2", 15);
        this.testSub7 = createSubscription(owner, "test-prod-1", "up1", "ue7", "uc2", 10);
        this.testSub8 = createSubscription(owner, "test-prod-1", "up1", "ue8", "uc2", 5);
        this.testSub9 = createSubscription(owner, "test-prod-1", "up1", "", "", 15);
        this.testSub10 = createSubscription(owner, "test-prod-1", "up1", "", "", 10);
        this.testSub11 = createSubscription(owner, "test-prod-1", "up1", "", "", 5);
        this.testSub12 = createSubscription(owner, "test-prod-1", "up1", "ue12", "uc3", 23);
        this.testSub13 = createSubscription(owner, "test-prod-1", "up1", "ue13", "uc3", 17);
        this.testSub14 = createSubscription(owner, "test-prod-1", "up1", "ue14", "uc3", 10);

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
                add(testSub1);
            }
        });

        // then
        verify(curator).create(testSub1);
        verify(curator, never()).delete(testSub1);
        verify(curator, never()).merge(testSub1);
        verify(sink, atLeastOnce()).emitSubscriptionCreated(testSub1);
    }

    @Test
    public void testSingleSubscriptionInDbAndListCausesMerge() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub1);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub1);
            }
        });

        // then
        verify(curator, never()).create(testSub1);
        verify(curator).merge(testSub1);
        verify(curator, never()).delete(testSub1);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub1, testSub1);
    }

    @Test
    public void testEmptyListCausesDbRemove() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub1);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>());

        // then
        verify(curator, never()).create(testSub1);
        verify(curator, never()).merge(testSub1);
        verify(curator).delete(testSub1);
        verify(sink, atLeastOnce()).createSubscriptionDeleted(testSub1);
        verify(sink, atLeastOnce()).sendEvent(any(Event.class));
    }

    @Test
    public void testOneExistOneNew() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub2);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub2);
                add(testSub3);
            }
        });

        // then
        verify(curator).merge(testSub2);
        verify(curator).create(testSub3);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub2, testSub2);
        verify(sink, atLeastOnce()).emitSubscriptionCreated(testSub3);
    }

    @Test
    public void testTwoExistOneNew() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub2);
                add(testSub3);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub3);
            }
        });

        // then
        verify(curator).delete(testSub2);
        verify(curator).merge(testSub3);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub3, testSub3);
        verify(sink, atLeastOnce()).createSubscriptionDeleted(testSub2);
    }

    @Test
    public void testThreeExistThreeNewOneDifferent() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub2);
                add(testSub3);
                add(testSub4);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub2);
                add(testSub4);
                add(testSub5);
            }
        });

        // then
        verify(curator).merge(testSub2);
        verify(curator).merge(testSub4);
        verify(curator).merge(testSub5);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub2, testSub2);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub4, testSub4);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub3, testSub5);
        verify(curator, never()).create(testSub5);
        verify(curator, never()).delete(testSub3);
    }

    @Test
    public void testThreeExistThreeNewConsumer() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub3);
                add(testSub4);
                add(testSub5);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub6);
                add(testSub7);
                add(testSub8);
            }
        });

        // then
        verify(curator).merge(testSub6);
        verify(curator).merge(testSub7);
        verify(curator).merge(testSub8);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub3, testSub6);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub4, testSub7);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub5, testSub8);
        verify(curator, never()).create(testSub6);
        verify(curator, never()).create(testSub7);
        verify(curator, never()).create(testSub8);
        verify(curator, never()).delete(testSub3);
        verify(curator, never()).delete(testSub4);
        verify(curator, never()).delete(testSub5);
    }

    @Test
    public void testThreeExistTwoNewConsumer() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub3);
                add(testSub4);
                add(testSub5);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub6);
                add(testSub8);
            }
        });

        // then
        verify(curator).merge(testSub6);
        verify(curator, never()).merge(testSub7);
        verify(curator).merge(testSub8);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub3, testSub6);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub5, testSub8);
        verify(curator, never()).create(testSub6);
        verify(curator, never()).create(testSub8);
        verify(curator, never()).delete(testSub3);
        verify(curator).delete(testSub4);
        verify(curator, never()).delete(testSub5);
    }

    @Test
    public void testTwoExistThreeNewConsumer() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub3);
                add(testSub4);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub6);
                add(testSub7);
                add(testSub8);
            }
        });

        // then
        verify(curator).merge(testSub6);
        verify(curator).merge(testSub7);
        verify(curator, never()).merge(testSub8);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub3, testSub6);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub4, testSub7);
        verify(curator, never()).create(testSub6);
        verify(curator, never()).create(testSub7);
        verify(curator).create(testSub8);
    }

    @Test
    public void testThreeExistOldThreeNew() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub9);
                add(testSub10);
                add(testSub11);
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub3);
                add(testSub4);
                add(testSub5);
            }
        });

        // then
        verify(curator).merge(testSub3);
        verify(curator).merge(testSub4);
        verify(curator).merge(testSub5);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub9, testSub3);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub10, testSub4);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub11, testSub5);
        verify(curator, never()).create(testSub3);
        verify(curator, never()).create(testSub4);
        verify(curator, never()).create(testSub5);
        verify(curator, never()).delete(testSub9);
        verify(curator, never()).delete(testSub10);
        verify(curator, never()).delete(testSub11);
    }

    @Test
    public void testQuantMatchAllLower() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub1); //quantity 25
                add(testSub2); //quantity 20
                add(testSub3); //quantity 15
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub12); //quantity 23
                add(testSub13); //quantity 17
                add(testSub14); //quantity 10
            }
        });

        // then
        verify(curator).merge(testSub12);
        verify(curator).merge(testSub13);
        verify(curator).merge(testSub14);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub1, testSub12);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub2, testSub13);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub3, testSub14);
        verify(curator, never()).create(testSub12);
        verify(curator, never()).create(testSub13);
        verify(curator, never()).create(testSub14);
        verify(curator, never()).delete(testSub1);
        verify(curator, never()).delete(testSub2);
        verify(curator, never()).delete(testSub3);
    }

    @Test
    public void testQuantMatchMix() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub2); // quantity 20
                add(testSub3); // quantity 15
                add(testSub4); // quantity 10
                add(testSub5); // quantity 5
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub12); // quantity 23
                add(testSub14); // quantity 10
            }
        });

        // then
        verify(curator).merge(testSub12);
        verify(curator).merge(testSub14);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub2, testSub12);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub4, testSub14);
        verify(curator, never()).create(testSub12);
        verify(curator, never()).create(testSub13);
        verify(curator, never()).create(testSub14);
        verify(curator, never()).delete(testSub2);
        verify(curator).delete(testSub3);
        verify(curator, never()).delete(testSub4);
        verify(curator).delete(testSub5);
    }

    private Subscription createSubscription(Owner owner, String productId,
            String poolId, String entId, String conId, long quantity) {
        Subscription sub = new Subscription();
        sub.setProduct(new Product(productId, productId));
        sub.setUpstreamPoolId(poolId);
        sub.setUpstreamEntitlementId(entId);
        sub.setUpstreamConsumerId(conId);
        sub.setQuantity(quantity);
        sub.setOwner(owner);
        sub.setId("" + index++);
        return sub;
    }
}
