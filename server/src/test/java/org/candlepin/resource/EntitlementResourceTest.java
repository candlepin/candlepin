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
package org.candlepin.resource;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.anyString;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.Entitler;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;



/**
 * EntitlementResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EntitlementResourceTest {

    private I18n i18n;
    private Consumer consumer;
    private Owner owner;
    @Mock private ProductServiceAdapter prodAdapter;
    @Mock private ProductCurator prodCurator;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private CandlepinPoolManager poolManager;
    @Mock private Entitler entitler;
    @Mock private SubscriptionResource subResource;
    @Mock private EntitlementRules entRules;
    @Mock private EntitlementRulesTranslator messageTranslator;
    @Mock protected ModelTranslator modelTranslator;

    private EntitlementResource entResource;


    @Before
    public void before() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        entResource = new EntitlementResource(entitlementCurator, consumerCurator,
            poolManager, i18n, entitler, entRules, messageTranslator, modelTranslator);
        owner = new Owner("admin");
        consumer = new Consumer("myconsumer", "bill", owner,
            TestUtil.createConsumerType());
    }

    @Test
    public void getUpstreamCertSimple() {
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");

        SubscriptionsCertificate subcert = new SubscriptionsCertificate();
        subcert.setCert("HELLO");
        subcert.setKey("CERT");

        e.getPool().setCertificate(subcert);

        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);

        String expected = "HELLOCERT";
        String result = entResource.getUpstreamCert(e.getId());

        assertEquals(expected, result);
    }

    @Test(expected = NotFoundException.class)
    public void getUpstreamCertSimpleNothingFound() {
        // Entitlement from stack sub-pool:
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");
        e.getPool().setSourceSubscription(null);
        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);
        entResource.getUpstreamCert(e.getId());
    }

    @Test
    public void getUpstreamCertStackSubPool() {
        Entitlement parentEnt = TestUtil.createEntitlement();
        parentEnt.setId("parentEnt");

        SubscriptionsCertificate subcert = new SubscriptionsCertificate();
        subcert.setCert("HELLO");
        subcert.setKey("CERT");

        parentEnt.getPool().setCertificate(subcert);

        when(entitlementCurator.findUpstreamEntitlementForStack(consumer, "mystack"))
            .thenReturn(parentEnt);

        String expected = "HELLOCERT";

        // Entitlement from stack sub-pool:
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");
        e.getPool().setSourceStack(new SourceStack(consumer, "mystack"));

        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);

        String result = entResource.getUpstreamCert(e.getId());
        assertEquals(expected, result);
    }

    @Test(expected = NotFoundException.class)
    public void getUpstreamCertStackSubPoolNothingFound() {
        when(entitlementCurator.findUpstreamEntitlementForStack(consumer, "mystack"))
            .thenReturn(null);

        // Entitlement from stack sub-pool:
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");
        e.getPool().setSourceStack(new SourceStack(consumer, "mystack"));
        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);

        entResource.getUpstreamCert(e.getId());
    }

    @Test(expected = BadRequestException.class)
    public void migrateEntitlementQuantityFail() {
        ConsumerType ct = TestUtil.createConsumerType();
        ct.setManifest(true);
        Entitlement e = TestUtil.createEntitlement();
        Consumer sourceConsumer = new Consumer("source-consumer", "bill", owner, ct);
        Consumer destConsumer = new Consumer("destination-consumer", "bill", owner, ct);
        e.setConsumer(sourceConsumer);
        e.setQuantity(25);

        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);
        when(consumerCurator.verifyAndLookupConsumer(eq(destConsumer.getUuid())))
            .thenReturn(destConsumer);

        entResource.migrateEntitlement(e.getId(), destConsumer.getUuid(), 30);
    }

    @Test(expected = BadRequestException.class)
    public void migrateEntitlementSourceConsumerFail() {
        ConsumerType ct = TestUtil.createConsumerType();
        ct.setManifest(true);
        Entitlement e = TestUtil.createEntitlement();
        Consumer destConsumer = new Consumer("destination-consumer", "bill", owner, ct);
        e.setConsumer(consumer);
        e.setQuantity(25);

        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);
        when(consumerCurator.verifyAndLookupConsumer(eq(destConsumer.getUuid()))).thenReturn(destConsumer);

        entResource.migrateEntitlement(e.getId(), destConsumer.getUuid(), 15);
    }

    @Test(expected = BadRequestException.class)
    public void migrateEntitlementDestinationConsumerFail() {
        ConsumerType ct = TestUtil.createConsumerType();
        ct.setManifest(true);
        Entitlement e = TestUtil.createEntitlement();
        Consumer sourceConsumer = new Consumer("source-consumer", "bill", owner, ct);
        e.setConsumer(sourceConsumer);
        e.setQuantity(25);

        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);
        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);

        entResource.migrateEntitlement(e.getId(), consumer.getUuid(), 15);
    }

    @Test(expected = BadRequestException.class)
    public void migrateEntitlementSameOwnerFail() {
        ConsumerType ct = TestUtil.createConsumerType();
        ct.setManifest(true);
        Entitlement e = TestUtil.createEntitlement();
        Owner owner2 = new Owner("admin2");
        Consumer sourceConsumer = new Consumer("source-consumer", "bill", owner, ct);
        Consumer destConsumer = new Consumer("destination-consumer", "bill", owner2, ct);
        e.setConsumer(sourceConsumer);
        e.setQuantity(25);

        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);
        when(consumerCurator.verifyAndLookupConsumer(eq(destConsumer.getUuid())))
            .thenReturn(destConsumer);

        entResource.migrateEntitlement(e.getId(), destConsumer.getUuid(), 15);
    }

    @Test
    public void getAllEntitlements() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Entitlement e = TestUtil.createEntitlement();
        e.setId("getEntitlementList");
        List<Entitlement> entitlements = new ArrayList<Entitlement>();
        entitlements.add(e);
        Page<List<Entitlement>> page = new Page<List<Entitlement>>();
        page.setPageData(entitlements);

        EntitlementDTO entitlementDTO = new EntitlementDTO();
        entitlementDTO.setId("getEntitlementList");

        when(entitlementCurator.listAll(isA(EntitlementFilterBuilder.class), isA(PageRequest.class)))
                .thenReturn(page);
        when(modelTranslator.translate(isA(Entitlement.class),
                eq(EntitlementDTO.class))).thenReturn(entitlementDTO);

        List<EntitlementDTO> result = entResource.listAllForConsumer(null, null, null, req);

        assertEquals(1, result.size());
        assertEquals("getEntitlementList", result.get(0).getId());
    }

    @Test
    public void getAllEntitlementsForConsumer() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);
        Pool pool = TestUtil.createPool(owner, TestUtil.createProduct());

        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool, null);
        e.setId("getAllEntitlementsForConsumer");
        List<Entitlement> entitlements = new ArrayList<Entitlement>();
        entitlements.add(e);
        Page<List<Entitlement>> page = new Page<List<Entitlement>>();
        page.setPageData(entitlements);

        EntitlementDTO entitlementDTO = new EntitlementDTO();
        entitlementDTO.setId("getAllEntitlementsForConsumer");

        when(consumerCurator.findByUuid(eq(consumer.getUuid()))).thenReturn(consumer);
        when(
                entitlementCurator.listByConsumer(isA(Consumer.class), anyString(),
                        isA(EntitlementFilterBuilder.class), isA(PageRequest.class))).thenReturn(page);
        when(modelTranslator.translate(isA(Entitlement.class),
            eq(EntitlementDTO.class))).thenReturn(entitlementDTO);

        List<EntitlementDTO> result = entResource.listAllForConsumer(consumer.getUuid(), null, null, req);

        assertEquals(1, result.size());
        assertEquals("getAllEntitlementsForConsumer", result.get(0).getId());
    }
}
