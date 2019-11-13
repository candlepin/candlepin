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

import org.candlepin.async.JobManager;
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
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.policy.entitlement.EntitlementRules;
import org.candlepin.policy.entitlement.EntitlementRulesTranslator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.when;



/**
 * EntitlementResourceTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EntitlementResourceTest {

    private I18n i18n;
    private Consumer consumer;
    private Owner owner;
    @Mock private ProductServiceAdapter prodAdapter;
    @Mock private ProductCurator prodCurator;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private CandlepinPoolManager poolManager;
    @Mock private Entitler entitler;
    @Mock private SubscriptionResource subResource;
    @Mock private EntitlementRules entRules;
    @Mock private EntitlementRulesTranslator messageTranslator;
    @Mock private JobManager jobManager;
    @Mock protected ModelTranslator modelTranslator;

    private EntitlementResource entResource;

    @BeforeEach
    public void before() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        entResource = new EntitlementResource(entitlementCurator, consumerCurator, consumerTypeCurator,
            poolManager, i18n, entitler, entRules, messageTranslator, jobManager, modelTranslator);

        owner = new Owner("admin");
        owner.setId("admin-id");

        ConsumerType ctype = TestUtil.createConsumerType();
        this.mockConsumerType(ctype);

        consumer = new Consumer("myconsumer", "bill", owner, ctype).setUuid(Util.generateUUID());
    }

    protected ConsumerType mockConsumerType(ConsumerType ctype) {
        if (ctype != null) {
            // Ensure the type has an ID
            if (ctype.getId() == null) {
                ctype.setId("test-ctype-" + ctype.getLabel() + "-" + TestUtil.randomInt());
            }

            when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()))).thenReturn(ctype);
            when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()), anyBoolean())).thenReturn(ctype);
            when(consumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);

            doAnswer(new Answer<ConsumerType>() {
                @Override
                public ConsumerType answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Consumer consumer = (Consumer) args[0];
                    ConsumerTypeCurator curator = (ConsumerTypeCurator) invocation.getMock();
                    ConsumerType ctype = null;

                    if (consumer == null || consumer.getTypeId() == null) {
                        throw new IllegalArgumentException("consumer is null or lacks a type ID");
                    }

                    ctype = curator.get(consumer.getTypeId());
                    if (ctype == null) {
                        throw new IllegalStateException("No such consumer type: " + consumer.getTypeId());
                    }

                    return ctype;
                }
            }).when(consumerTypeCurator).getConsumerType(any(Consumer.class));
        }

        return ctype;
    }

    @Test
    public void getUpstreamCertSimple() {
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");

        SubscriptionsCertificate subcert = new SubscriptionsCertificate();
        subcert.setCert("HELLO");
        subcert.setKey("CERT");

        e.getPool().setCertificate(subcert);

        when(entitlementCurator.get(eq(e.getId()))).thenReturn(e);

        String expected = "HELLOCERT";
        String result = entResource.getUpstreamCert(e.getId());

        assertEquals(expected, result);
    }

    @Test
    public void getUpstreamCertSimpleNothingFound() {
        // Entitlement from stack sub-pool:
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");
        e.getPool().setSourceSubscription(null);
        when(entitlementCurator.get(eq(e.getId()))).thenReturn(e);
        assertThrows(NotFoundException.class, () -> entResource.getUpstreamCert(e.getId()));
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

        when(entitlementCurator.get(eq(e.getId()))).thenReturn(e);

        String result = entResource.getUpstreamCert(e.getId());
        assertEquals(expected, result);
    }

    @Test
    public void getUpstreamCertStackSubPoolNothingFound() {
        when(entitlementCurator.findUpstreamEntitlementForStack(consumer, "mystack"))
            .thenReturn(null);

        // Entitlement from stack sub-pool:
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");
        e.getPool().setSourceStack(new SourceStack(consumer, "mystack"));
        when(entitlementCurator.get(eq(e.getId()))).thenReturn(e);

        assertThrows(NotFoundException.class, () -> entResource.getUpstreamCert(e.getId()));
    }

    @Test
    public void migrateEntitlementQuantityFail() {
        ConsumerType ct = TestUtil.createConsumerType();
        ct.setManifest(true);
        this.mockConsumerType(ct);

        Entitlement e = TestUtil.createEntitlement();
        Consumer sourceConsumer = new Consumer("source-consumer", "bill", owner, ct)
            .setUuid(Util.generateUUID());
        Consumer destConsumer = new Consumer("destination-consumer", "bill", owner, ct)
            .setUuid(Util.generateUUID());
        e.setConsumer(sourceConsumer);
        e.setQuantity(25);

        when(entitlementCurator.get(eq(e.getId()))).thenReturn(e);
        when(consumerCurator.verifyAndLookupConsumer(eq(destConsumer.getUuid())))
            .thenReturn(destConsumer);

        assertThrows(BadRequestException.class, () ->
            entResource.migrateEntitlement(e.getId(), destConsumer.getUuid(), 30)
        );
    }

    @Test
    public void migrateEntitlementSourceConsumerFail() {
        ConsumerType ct = TestUtil.createConsumerType();
        ct.setManifest(true);
        this.mockConsumerType(ct);

        Entitlement e = TestUtil.createEntitlement();
        Consumer destConsumer = new Consumer("destination-consumer", "bill", owner, ct)
            .setUuid(Util.generateUUID());
        e.setConsumer(consumer);
        e.setQuantity(25);

        when(entitlementCurator.get(eq(e.getId()))).thenReturn(e);
        when(consumerCurator.verifyAndLookupConsumer(eq(destConsumer.getUuid()))).thenReturn(destConsumer);

        assertThrows(BadRequestException.class, () ->
            entResource.migrateEntitlement(e.getId(), destConsumer.getUuid(), 15)
        );
    }

    @Test
    public void migrateEntitlementDestinationConsumerFail() {
        ConsumerType ct = TestUtil.createConsumerType();
        ct.setManifest(true);
        this.mockConsumerType(ct);

        Entitlement e = TestUtil.createEntitlement();
        Consumer sourceConsumer = new Consumer("source-consumer", "bill", owner, ct)
            .setUuid(Util.generateUUID());
        e.setConsumer(sourceConsumer);
        e.setQuantity(25);

        when(entitlementCurator.get(eq(e.getId()))).thenReturn(e);
        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);

        assertThrows(BadRequestException.class, () ->
            entResource.migrateEntitlement(e.getId(), consumer.getUuid(), 15)
        );
    }

    @Test
    public void migrateEntitlementSameOwnerFail() {
        ConsumerType ct = TestUtil.createConsumerType();
        ct.setManifest(true);
        this.mockConsumerType(ct);

        Entitlement e = TestUtil.createEntitlement();
        Owner owner2 = new Owner("admin2");
        owner.setId(TestUtil.randomString());
        owner2.setId(TestUtil.randomString());
        Consumer sourceConsumer = new Consumer("source-consumer", "bill", owner, ct)
            .setUuid(Util.generateUUID());
        Consumer destConsumer = new Consumer("destination-consumer", "bill", owner2, ct)
            .setUuid(Util.generateUUID());
        e.setConsumer(sourceConsumer);
        e.setQuantity(25);

        when(entitlementCurator.get(eq(e.getId()))).thenReturn(e);
        when(consumerCurator.verifyAndLookupConsumer(eq(destConsumer.getUuid())))
            .thenReturn(destConsumer);

        assertThrows(BadRequestException.class, () ->
            entResource.migrateEntitlement(e.getId(), destConsumer.getUuid(), 15)
        );
    }

    @Test
    public void getAllEntitlements() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Entitlement e = TestUtil.createEntitlement();
        e.setId("getEntitlementList");
        List<Entitlement> entitlements = new ArrayList<>();
        entitlements.add(e);
        Page<List<Entitlement>> page = new Page<>();
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
        List<Entitlement> entitlements = new ArrayList<>();
        entitlements.add(e);
        Page<List<Entitlement>> page = new Page<>();
        page.setPageData(entitlements);

        EntitlementDTO entitlementDTO = new EntitlementDTO();
        entitlementDTO.setId("getAllEntitlementsForConsumer");

        when(consumerCurator.findByUuid(eq(consumer.getUuid()))).thenReturn(consumer);
        when(entitlementCurator.listByConsumer(any(Consumer.class), nullable(String.class),
            any(EntitlementFilterBuilder.class), any(PageRequest.class))).thenReturn(page);
        when(modelTranslator.translate(any(Entitlement.class),
            eq(EntitlementDTO.class))).thenReturn(entitlementDTO);

        List<EntitlementDTO> result = entResource.listAllForConsumer(consumer.getUuid(), null, null, req);

        assertEquals(1, result.size());
        assertEquals("getAllEntitlementsForConsumer", result.get(0).getId());
    }
}
