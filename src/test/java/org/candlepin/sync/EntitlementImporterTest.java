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

import static org.mockito.Mockito.mock;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventSink;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.SubProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionCurator;
import org.candlepin.test.TestUtil;
import org.codehaus.jackson.map.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * EntitlementImporterTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EntitlementImporterTest {

    @Mock private EventSink sink;
    @Mock private SubscriptionCurator curator;
    @Mock private CertificateSerialCurator certSerialCurator;
    @Mock private ObjectMapper om;

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
    private Subscription testSub15;
    private Subscription testSub16;
    private Subscription testSub20;
    private Subscription testSub21;
    private Subscription testSub22;
    private Subscription testSub23;
    private Subscription testSub24;
    private Subscription testSub30;
    private Subscription testSub31;
    private Subscription testSub32;
    private Subscription testSub33;
    private Subscription testSub34;
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
        this.testSub15 = createSubscription(owner, "test-prod-1", "up1", "ue15", "uc1", 15);
        this.testSub16 = createSubscription(owner, "test-prod-1", "up1", "ue16", "uc1", 15);
        this.testSub20 = createSubscription(owner, "test-prod-1", "up2", "ue20", "uc1", 25);
        this.testSub21 = createSubscription(owner, "test-prod-1", "up2", "ue21", "uc1", 20);
        this.testSub22 = createSubscription(owner, "test-prod-1", "up2", "ue22", "uc1", 15);
        this.testSub23 = createSubscription(owner, "test-prod-1", "up2", "ue23", "uc1", 10);
        this.testSub24 = createSubscription(owner, "test-prod-1", "up2", "ue24", "uc1", 5);
        this.testSub30 = createSubscription(owner, "test-prod-1", "up3", "ue30", "uc1", 25);
        this.testSub31 = createSubscription(owner, "test-prod-1", "up3", "ue31", "uc1", 20);
        this.testSub32 = createSubscription(owner, "test-prod-1", "up3", "ue32", "uc1", 15);
        this.testSub33 = createSubscription(owner, "test-prod-1", "up3", "ue33", "uc1", 10);
        this.testSub34 = createSubscription(owner, "test-prod-1", "up3", "ue34", "uc1", 5);

        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.importer = new EntitlementImporter(this.curator, certSerialCurator, this.sink, i18n);
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

    @Test
    public void testQuantMatchAllSame() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub3); // quantity 15
                add(testSub15); // quantity 15
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub3); // quantity 15
                add(testSub16); // quantity 15
            }
        });

        // then
        verify(curator).merge(testSub3);
        verify(curator).merge(testSub16);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub3, testSub3);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub15, testSub16);
        verify(curator, never()).create(testSub3);
        verify(curator, never()).create(testSub15);
        verify(curator, never()).create(testSub16);
        verify(curator, never()).delete(testSub3);
        verify(curator, never()).delete(testSub15);
        verify(curator, never()).delete(testSub16);
    }

    @Test
    public void testMultiPools() {
        // given
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub1); // quantity 25
                add(testSub2); // quantity 20
                add(testSub3); // quantity 15
                add(testSub4); // quantity 10
                add(testSub5); // quantity 5
                add(testSub20); // quantity 25
                add(testSub21); // quantity 20
                add(testSub22); // quantity 15
                add(testSub23); // quantity 10
                add(testSub24); // quantity 5
            }
        });

        // when
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub1); // quantity 25
                add(testSub2); // quantity 20
                add(testSub3); // quantity 15
                add(testSub4); // quantity 10
                add(testSub5); // quantity 5
                add(testSub30); // quantity 25
                add(testSub31); // quantity 20
                add(testSub32); // quantity 15
                add(testSub33); // quantity 10
                add(testSub34); // quantity 5
            }
        });

        // then
        verify(curator).merge(testSub1);
        verify(curator).merge(testSub2);
        verify(curator).merge(testSub3);
        verify(curator).merge(testSub4);
        verify(curator).merge(testSub5);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub1, testSub1);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub2, testSub2);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub3, testSub3);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub4, testSub4);
        verify(sink, atLeastOnce()).emitSubscriptionModified(testSub5, testSub5);
        verify(curator).delete(testSub20);
        verify(curator).delete(testSub21);
        verify(curator).delete(testSub22);
        verify(curator).delete(testSub23);
        verify(curator).delete(testSub24);
        verify(curator).create(testSub30);
        verify(curator).create(testSub31);
        verify(curator).create(testSub32);
        verify(curator).create(testSub33);
        verify(curator).create(testSub34);
    }

    @Test
    public void importObject() throws Exception {
        Consumer consumer = TestUtil.createConsumer(owner);
        ConsumerDto consumerDto = new ConsumerDto(consumer.getUuid(), consumer.getName(),
            consumer.getType(), consumer.getOwner(), "", "");

        Product parentProduct = TestUtil.createProduct();
        ProvidedProduct pp1 = TestUtil.createProvidedProduct();

        Set<ProvidedProduct> provided = new HashSet<ProvidedProduct>();
        provided.add(pp1);

        // Sub product setup
        Product subProduct = TestUtil.createProduct();
        SubProvidedProduct subProvided1 = TestUtil.createSubProvidedProduct();

        Set<SubProvidedProduct> subProvidedProducts = new HashSet<SubProvidedProduct>();
        subProvidedProducts.add(subProvided1);

        Pool pool = TestUtil.createPool(owner, parentProduct, provided, subProduct.getId(),
            subProvidedProducts, 3);
        EntitlementCertificate cert = createEntitlementCertificate("my-test-key", "my-cert");
        Entitlement ent = TestUtil.createEntitlement(owner, consumer, pool, cert);
        ent.setQuantity(3);

        Reader reader = mock(Reader.class);
        when(om.readValue(reader, Entitlement.class)).thenReturn(ent);

        // Create our expected products
        Map<String, Product> productsById = new HashMap<String, Product>();
        productsById.put(parentProduct.getId(), parentProduct);
        productsById.put(pp1.getProductId(),
            TestUtil.createProduct(pp1.getProductId(), pp1.getProductName()));
        productsById.put(subProduct.getId(), subProduct);
        productsById.put(subProvided1.getProductId(),TestUtil.createProduct(
            subProvided1.getProductId(), subProvided1.getProductName()));

        Subscription sub = importer.importObject(om, reader, owner,
            productsById, consumerDto);

        assertEquals(pool.getId(), sub.getUpstreamPoolId());
        assertEquals(consumer.getUuid(), sub.getUpstreamConsumerId());
        assertEquals(ent.getId(), sub.getUpstreamEntitlementId());

        assertEquals(owner, sub.getOwner());
        assertEquals(ent.getStartDate(), sub.getStartDate());
        assertEquals(ent.getEndDate(), sub.getEndDate());

        assertEquals(pool.getAccountNumber(), sub.getAccountNumber());
        assertEquals(pool.getContractNumber(), sub.getContractNumber());

        assertEquals(ent.getQuantity().intValue(), sub.getQuantity().intValue());

        assertEquals(parentProduct, sub.getProduct());
        assertEquals(provided.size(), sub.getProvidedProducts().size());
        assertEquals(pp1.getProductId(), sub.getProvidedProducts().
            iterator().next().getId());

        assertEquals(subProduct, sub.getSubProduct());
        assertEquals(1, sub.getSubProvidedProducts().size());
        assertEquals(subProvided1.getProductId(), sub.getSubProvidedProducts().
            iterator().next().getId());

        assertNotNull(sub.getCertificate());
        CertificateSerial serial = sub.getCertificate().getSerial();
        assertEquals(cert.getSerial().isCollected(), serial.isCollected());
        assertEquals(cert.getSerial().isRevoked(), serial.isRevoked());
        assertEquals(cert.getSerial().getExpiration(), serial.getExpiration());
        assertEquals(cert.getSerial().getCreated(), serial.getCreated());
        assertEquals(cert.getSerial().getUpdated(), serial.getUpdated());
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

    private void addSubProductData(Product subProduct, Set<SubProvidedProduct> subProvided) {

    }

    protected EntitlementCertificate createEntitlementCertificate(String key,
        String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(new Date());
        certSerial.setCollected(true);
        certSerial.setRevoked(true);
        certSerial.setUpdated(new Date());
        certSerial.setCreated(new Date());
        toReturn.setKeyAsBytes(key.getBytes());
        toReturn.setCertAsBytes(cert.getBytes());
        toReturn.setSerial(certSerial);
        return toReturn;
    }
}
