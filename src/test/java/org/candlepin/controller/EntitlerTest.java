/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// TODO: FIXME: Rewrite this test to not be so reliant upon mocks. It's making things incredibly brittle and
// wasting dev time tracking down non-issues when a mock silently fails because the implementation changes.



@ExtendWith(MockitoExtension.class)
public class EntitlerTest {
    private I18n i18n;
    private Entitler entitler;
    private EntitlementRulesTranslator translator;

    @Mock
    private PoolManager pm;
    @Mock
    private EventFactory ef;
    @Mock
    private EventSink sink;
    @Mock
    private Owner owner;
    @Mock
    private Consumer consumer;
    @Mock
    private ConsumerCurator cc;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private Configuration config;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private PoolCurator poolCurator;
    @Mock
    private ProductServiceAdapter productAdapter;
    @Mock
    private PoolService poolService;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;

    @Mock
    private ContentCurator mockContentCurator;

    @Mock
    private ProductCurator mockProductCurator;

    private ValidationResult fakeOutResult(String msg) {
        ValidationResult result = new ValidationResult();
        ValidationError err = new ValidationError(msg);
        result.addError(err);
        return result;
    }

    @BeforeEach
    public void init() {
        i18n = I18nFactory.getI18n(
            getClass(),
            Locale.US,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK);
        translator = new EntitlementRulesTranslator(i18n);

        entitler = new Entitler(pm, poolService, cc, i18n, ef, sink, translator, entitlementCurator, config,
            ownerCurator, poolCurator, consumerTypeCurator);
    }

    @Test
    public void bindByPoolString() throws EntitlementRefusedException {
        String poolid = "pool10";
        Entitlement ent = mock(Entitlement.class);
        List<Entitlement> eList = new ArrayList<>();
        eList.add(ent);
        when(cc.findByUuid("abcd1234")).thenReturn(consumer);

        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(poolid, 1);

        when(pm.entitleByPools(consumer, pQs)).thenReturn(eList);

        List<Entitlement> ents = entitler.bindByPoolQuantities("abcd1234", pQs);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByPool() throws EntitlementRefusedException {
        String poolid = "pool10";
        Entitlement ent = mock(Entitlement.class);
        List<Entitlement> eList = new ArrayList<>();
        eList.add(ent);

        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(poolid, 1);
        when(pm.entitleByPools(consumer, pQs)).thenReturn(eList);

        List<Entitlement> ents = entitler.bindByPoolQuantity(consumer, poolid, 1);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByProductsString() throws Exception {
        Owner owner = new Owner()
            .setId(TestUtil.randomString())
            .setKey("o1")
            .setDisplayName("o1")
            .setContentAccessMode("entitlement");

        Set<String> pids = Set.of("prod1", "prod2", "prod3");
        when(cc.findByUuid("abcd1234")).thenReturn(consumer);
        when(consumer.getOwnerId()).thenReturn(owner.getOwnerId());
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);

        entitler.bindByProducts(pids, "abcd1234", null, null);

        AutobindData data = new AutobindData(consumer, this.owner)
            .forProducts(pids);

        verify(pm).entitleByProducts(data);
    }

    @Test
    public void bindByProducts() throws Exception {
        Set<String> pids = Set.of("prod1", "prod2", "prod3");
        AutobindData data = new AutobindData(consumer, owner)
            .forProducts(pids);

        entitler.bindByProducts(data);

        verify(pm).entitleByProducts(data);
    }

    @Test
    public void bindByProductsConsumerDevAutoDisabled() {
        AutobindData data = new AutobindData(consumer, owner);
        when(owner.isAutobindDisabled()).thenReturn(true);
        assertThrows(AutobindDisabledForOwnerException.class, () -> entitler.bindByProducts(data));
    }

    @Test
    public void bindByProductsConsumerDevAutoHypervisorDisabledHypervisor() {
        AutobindData data = new AutobindData(consumer, owner);
        when(owner.isAutobindDisabled()).thenReturn(false);
        when(owner.isAutobindHypervisorDisabled()).thenReturn(true);
        when(consumerTypeCurator.getConsumerType(eq(consumer)))
            .thenReturn(new ConsumerType(ConsumerType.ConsumerTypeEnum.HYPERVISOR));
        assertThrows(AutobindHypervisorDisabledException.class, () -> entitler.bindByProducts(data));
    }

    @Test
    public void bindByProductsConsumerDevAutoDisabledSCA() throws Exception {
        AutobindData data = new AutobindData(consumer, owner);
        when(owner.isUsingSimpleContentAccess()).thenReturn(true);
        assertEquals(0, entitler.bindByProducts(data).size());
    }

    @Test
    public void adjustEntitlementQuantity() {
        Entitlement ent = mock(Entitlement.class);
        assertDoesNotThrow(() -> entitler.adjustEntitlementQuantity(consumer, ent, 4));
    }

    @Test
    public void adjustEntitlementQuantityException() throws Exception {
        Entitlement ent = mock(Entitlement.class);
        ValidationResult vr = new ValidationResult();
        vr.addError("Error");
        Map<String, ValidationResult> results = Map.of("test", vr);
        when(pm.adjustEntitlementQuantity(consumer, ent, 4))
            .thenThrow(new EntitlementRefusedException(results));
        assertThrows(ForbiddenException.class, () -> entitler.adjustEntitlementQuantity(consumer, ent, 4));
    }

    @Test
    public void nullPool() throws EntitlementRefusedException {
        String poolid = "foo";
        Consumer c = TestUtil.createConsumer(); // keeps me from casting null
        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(poolid, 1);
        when(cc.findByUuid(c.getUuid())).thenReturn(c);
        when(pm.entitleByPools(c, pQs)).thenThrow(new IllegalArgumentException());

        assertThrows(BadRequestException.class, () -> entitler.bindByPoolQuantities(c.getUuid(), pQs));
    }

    @Test
    public void someOtherErrorPool() {
        assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest("do.not.match"));
    }

    @Test
    public void consumerTypeMismatchPool() {
        String msg = "rulefailed.consumer.type.mismatch";
        assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
    }

    @Test
    public void alreadyHasProductPool() {
        String msg = "rulefailed.consumer.already.has.product";
        assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
    }

    @Test
    public void noEntitlementsAvailable() {
        String msg = "rulefailed.no.entitlements.available";
        assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
    }

    @Test
    public void consumerDoesntSupportInstanceBased() {
        String expected = "Unit does not support instance based calculation required by pool \"pool10\"";
        String msg = "rulefailed.instance.unsupported.by.consumer";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void consumerDoesntSupportCores() {
        String expected = "Unit does not support core calculation required by pool \"pool10\"";
        String msg = "rulefailed.cores.unsupported.by.consumer";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void consumerDoesntSupportRam() {
        String expected = "Unit does not support RAM calculation required by pool \"pool10\"";
        String msg = "rulefailed.ram.unsupported.by.consumer";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void consumerDoesntSupportDerived() {
        String expected = "Unit does not support derived products data required by pool \"pool10\"";
        String msg = "rulefailed.derivedproduct.unsupported.by.consumer";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    private void bindByPoolErrorTest(String msg) throws EntitlementRefusedException {
        String poolid = "pool10";
        Pool pool = mock(Pool.class);
        Map<String, ValidationResult> fakeResult = new HashMap<>();
        fakeResult.put(poolid, fakeOutResult(msg));
        EntitlementRefusedException ere = new EntitlementRefusedException(fakeResult);

        when(pool.getId()).thenReturn(poolid);
        when(poolCurator.get(poolid)).thenReturn(pool);
        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(poolid, 1);
        when(pm.entitleByPools(consumer, pQs)).thenThrow(ere);
        entitler.bindByPoolQuantity(consumer, poolid, 1);
    }

    @Test
    public void alreadyHasProduct() {
        String msg = "rulefailed.consumer.already.has.product";
        assertThrows(ForbiddenException.class, () -> bindByProductErrorTest(msg));
    }

    @Test
    public void noEntitlementsForProduct() {
        String msg = "rulefailed.no.entitlements.available";
        assertThrows(ForbiddenException.class, () -> bindByProductErrorTest(msg));
    }

    @Test
    public void mismatchByProduct() {
        String msg = "rulefailed.consumer.type.mismatch";
        assertThrows(ForbiddenException.class, () -> bindByProductErrorTest(msg));
    }

    @Test
    public void virtOnly() {
        String expected = "Pool is restricted to virtual guests: \"pool10\".";
        String msg = "rulefailed.virt.only";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void physicalOnly() {
        String expected = "Pool is restricted to physical systems: \"pool10\".";
        String msg = "rulefailed.physical.only";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void allOtherErrors() {
        assertThrows(ForbiddenException.class, () -> bindByProductErrorTest("generic.error"));
    }

    private void bindByProductErrorTest(String msg) throws EntitlementRefusedException,
        AutobindDisabledForOwnerException, AutobindHypervisorDisabledException {

        Set<String> pids = Set.of("prod1", "prod2", "prod3");
        Map<String, ValidationResult> fakeResult = new HashMap<>();
        fakeResult.put("blah", fakeOutResult(msg));

        EntitlementRefusedException ere = new EntitlementRefusedException(fakeResult);
        AutobindData data = new AutobindData(consumer, owner)
            .forProducts(pids);

        when(pm.entitleByProducts(data)).thenThrow(ere);
        entitler.bindByProducts(data);
    }

    @Test
    public void events() {
        List<Entitlement> ents = new ArrayList<>();
        ents.add(mock(Entitlement.class));
        ents.add(mock(Entitlement.class));

        Event evt1 = mock(Event.class);
        Event evt2 = mock(Event.class);
        when(ef.entitlementCreated(any(Entitlement.class)))
            .thenReturn(evt1)
            .thenReturn(evt2);
        entitler.sendEvents(ents);

        verify(sink).queueEvent(evt1);
        verify(sink).queueEvent(evt2);
    }

    @Test
    public void noEventsWhenEntitlementsNull() {
        entitler.sendEvents(null);
        verify(sink, never()).queueEvent(any(Event.class));
    }

    @Test
    public void noEventsWhenListEmpty() {
        List<Entitlement> ents = new ArrayList<>();
        entitler.sendEvents(ents);
        verify(sink, never()).queueEvent(any(Event.class));
    }

    @Test
    public void testRevokesLapsedUnmappedGuestEntitlementsOnAutoHeal() throws Exception {
        Owner owner1 = new Owner()
            .setId(TestUtil.randomString())
            .setKey("o1")
            .setDisplayName("o1")
            .setContentAccessMode("entitlement");
        when(ownerCurator.findOwnerById(owner1.getId())).thenReturn(owner1);

        Product product = TestUtil.createProduct();

        Pool p1 = TestUtil.createPool(owner1, product);
        p1.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");

        Date thirtySixHoursAgo = new Date(new Date().getTime() - 36L * 60L * 60L * 1000L);
        Date twelveHoursAgo = new Date(new Date().getTime() - 12L * 60L * 60L * 1000L);

        Consumer consumer = TestUtil.createConsumer(owner1);
        consumer.setCreated(thirtySixHoursAgo);
        consumer.setFact("virt.uuid", "1");

        when(cc.findByUuid("abcd1234")).thenReturn(consumer);

        Entitlement e1 = TestUtil.createEntitlement(owner1, consumer, p1, null);
        e1.setEndDateOverride(twelveHoursAgo);

        p1.setEntitlements(Set.of(e1));

        when(entitlementCurator.findByPoolAttribute(consumer, "unmapped_guests_only", "true"))
            .thenReturn(List.of(e1));
        when(config.getInt(ConfigProperties.ENTITLER_BULK_SIZE)).thenReturn(1000);

        Set<String> pids = Set.of(product.getId(), "prod2");
        entitler.bindByProducts(pids, "abcd1234", null, null);

        AutobindData data = new AutobindData(consumer, owner1)
            .forProducts(pids);

        verify(pm).entitleByProducts(data);
        verify(poolService).revokeEntitlements(List.of(e1));
    }

    @Test
    public void testUnmappedGuestRevocation() {
        Pool pool1 = createValidPool("1");
        Pool pool2 = createExpiredPool("2");

        when(entitlementCurator.findByPoolAttribute("unmapped_guests_only", "true"))
            .thenReturn(entsOf(pool1, pool2));
        when(config.getInt(ConfigProperties.ENTITLER_BULK_SIZE)).thenReturn(1000);

        int total = entitler.revokeUnmappedGuestEntitlements();

        assertEquals(1, total);
        verify(poolService).revokeEntitlements(List.of(entOf(pool2)));
    }

    @Test
    public void unmappedGuestRevocationShouldBePartitioned() {
        Pool pool1 = createExpiredPool("1");
        Pool pool2 = createExpiredPool("2");

        when(entitlementCurator.findByPoolAttribute("unmapped_guests_only", "true"))
            .thenReturn(entsOf(pool1, pool2));
        when(config.getInt(ConfigProperties.ENTITLER_BULK_SIZE)).thenReturn(1);

        int total = entitler.revokeUnmappedGuestEntitlements();

        assertEquals(2, total);
        verify(poolService).revokeEntitlements(List.of(entOf(pool1)));
        verify(poolService).revokeEntitlements(List.of(entOf(pool2)));
    }

    private Pool createValidPool(String id) {
        Date expireInFuture = new Date(new Date().getTime() + 60L * 60L * 1000L);
        return createPool(id, expireInFuture);
    }

    private Pool createExpiredPool(String id) {
        Date thirtySixHoursAgo = new Date(new Date().getTime() - 36L * 60L * 60L * 1000L);
        return createPool(id, thirtySixHoursAgo);
    }

    public Pool createPool(String id, Date expireAt) {
        Owner owner = new Owner()
            .setId(id + "-id")
            .setKey(id)
            .setDisplayName(id);

        Product product = TestUtil.createProduct();
        Pool pool = TestUtil.createPool(owner, product);
        pool.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");

        Date twelveHoursAgo = new Date(new Date().getTime() - 12L * 60L * 60L * 1000L);

        Consumer c;
        c = TestUtil.createConsumer(owner);
        c.setCreated(twelveHoursAgo);

        Entitlement entitlement = TestUtil.createEntitlement(owner, c, pool, null);
        entitlement.setEndDateOverride(expireAt);
        Set<Entitlement> entitlements = new HashSet<>();
        entitlements.add(entitlement);

        pool.setEntitlements(entitlements);

        return pool;
    }

    private List<Entitlement> entsOf(Pool... pools) {
        return Arrays.stream(pools)
            .map(this::entOf)
            .collect(Collectors.toList());
    }

    private Entitlement entOf(Pool pool) {
        return pool.getEntitlements().stream()
            .findFirst()
            .orElseThrow(IllegalStateException::new);
    }
}
