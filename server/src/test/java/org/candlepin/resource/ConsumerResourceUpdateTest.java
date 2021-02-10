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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.v1.EnvironmentDTO;
import org.candlepin.dto.api.v1.GuestIdDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.FactValidator;

import com.google.inject.util.Providers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Provider;



/**
 * Test suite for the ConsumerResource class, focusing on updates
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsumerResourceUpdateTest {

    @Mock private UserServiceAdapter userService;
    @Mock private IdentityCertServiceAdapter idCertService;
    @Mock private ProductServiceAdapter productService;
    @Mock private SubscriptionServiceAdapter subscriptionService;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private EventSink sink;
    @Mock private EventFactory eventFactory;
    @Mock private ActivationKeyCurator activationKeyCurator;
    @Mock private PoolManager poolManager;
    @Mock private ComplianceRules complianceRules;
    @Mock private SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock private Entitler entitler;
    @Mock private DeletedConsumerCurator deletedConsumerCurator;
    @Mock private EnvironmentCurator environmentCurator;
    @Mock private EventBuilder consumerEventBuilder;
    @Mock private ConsumerBindUtil consumerBindUtil;
    @Mock private ConsumerEnricher consumerEnricher;
    @Mock private Principal principal;
    @Mock private JobManager jobManager;
    private ModelTranslator translator;

    private I18n i18n;
    private Provider<I18n> i18nProvider = () -> i18n;

    private ConsumerResource resource;
    private Provider<GuestMigration> migrationProvider;
    private GuestMigration testMigration;

    @BeforeEach
    public void init() throws Exception {
        Configuration config = new CandlepinCommonTestConfig();

        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        testMigration = new GuestMigration(consumerCurator);

        migrationProvider = Providers.of(testMigration);
        this.translator = new StandardTranslator(this.consumerTypeCurator,
            this.environmentCurator, this.ownerCurator);

        this.resource = new ConsumerResource(this.consumerCurator,
            this.consumerTypeCurator, null, this.subscriptionService, this.productService, null,
            this.idCertService, null, this.i18n, this.sink, this.eventFactory, null,
            this.userService, poolManager, null, ownerCurator,
            this.activationKeyCurator, this.entitler, this.complianceRules, this.systemPurposeComplianceRules,
            this.deletedConsumerCurator, this.environmentCurator, null,
            config, null, null, this.consumerBindUtil,
            null, null, new FactValidator(config, this.i18nProvider),
            null, consumerEnricher, migrationProvider, this.translator, this.jobManager);

        when(complianceRules.getStatus(any(Consumer.class), any(Date.class), any(Boolean.class),
            any(Boolean.class))).thenReturn(new ComplianceStatus(new Date()));

        when(idCertService.regenerateIdentityCert(any(Consumer.class)))
            .thenReturn(new IdentityCertificate());

        when(consumerEventBuilder.setEventData(any(Consumer.class)))
            .thenReturn(consumerEventBuilder);
        when(eventFactory.getEventBuilder(any(Target.class), any(Type.class)))
            .thenReturn(consumerEventBuilder);
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
    public void nothingChanged() throws Exception {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(), consumer, principal);
        verify(sink, never()).queueEvent((Event) any());
    }

    private Consumer getFakeConsumer() {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        this.mockConsumerType(ctype);

        Consumer consumer = new Consumer();
        Owner owner = new Owner();
        owner.setId("FAKEOWNERID");
        String uuid = "FAKEUUID";
        consumer.setUuid(uuid);
        consumer.setOwner(owner);
        consumer.setName("FAKENAME");
        consumer.setType(ctype);

        // go ahead and patch the curator to match it
        when(this.consumerCurator.findByUuid(uuid)).thenReturn(consumer);
        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(consumer);

        return consumer;
    }

    private ConsumerDTO getFakeConsumerDTO() {
        return translator.translate(getFakeConsumer(), ConsumerDTO.class);
    }

    @Test
    public void testUpdatesOnContentTagChanges() {
        HashSet<String> originalTags = new HashSet<>(Arrays.asList(new String[] { "hello", "world" }));
        HashSet<String> changedTags = new HashSet<>(Arrays.asList(new String[] { "x", "y" }));

        ConsumerDTO c = getFakeConsumerDTO();
        c.setContentTags(originalTags);

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.setContentTags(changedTags);

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        resource.updateConsumer(c.getUuid(), incoming, principal);
        verify(consumerCurator, times(1)).update(consumerCaptor.capture());
        Consumer mergedConsumer = consumerCaptor.getValue();

        assertEquals(changedTags, mergedConsumer.getContentTags());
    }

    @Test
    public void nullReleaseVer() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setReleaseVersion(null);

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.setReleaseVersion("not null");
        this.resource.updateConsumer(consumer.getUuid(), incoming, principal);

        ConsumerDTO consumer2 = getFakeConsumerDTO();
        consumer2.setReleaseVersion("foo");
        ConsumerDTO incoming2 = new ConsumerDTO();
        incoming2.setReleaseVersion(null);
        this.resource.updateConsumer(consumer2.getUuid(), incoming2, principal);
    }

    private void compareConsumerRelease(String release1, String release2, Boolean verify) {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setReleaseVersion(release1);

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.setReleaseVersion(release2);

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        this.resource.updateConsumer(consumer.getUuid(), incoming, principal);
        verify(consumerCurator, times(1)).update(consumerCaptor.capture());
        Consumer mergedConsumer = consumerCaptor.getValue();
        if (verify) {
            verify(sink).queueEvent((Event) any());
        }
        assertEquals(incoming.getReleaseVersion(), mergedConsumer.getReleaseVer().getReleaseVer());
    }

    @Test
    public void releaseVerChanged() {
        compareConsumerRelease("6.2", "6.2.1", true);
    }

    @Test
    public void releaseVerChangedEmpty() {
        compareConsumerRelease("", "6.2.1", true);
    }

    @Test
    public void releaseVerChangedNull() {
        compareConsumerRelease(null, "6.2.1", true);
    }

    @Test
    public void releaseVerNothingChangedEmpty() {
        compareConsumerRelease("", "", false);
    }

    @Test
    public void installedPackagesChanged() throws Exception {
        Product productA = TestUtil.createProduct("Product A");
        Product productB = TestUtil.createProduct("Product B");
        Product productC = TestUtil.createProduct("Product C");

        Consumer consumer = getFakeConsumer();
        consumer.addInstalledProduct(new ConsumerInstalledProduct(productA));
        consumer.addInstalledProduct(new ConsumerInstalledProduct(productB));

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.addInstalledProduct(new ConsumerInstalledProductDTO(productB.getId(), productB.getName()));
        incoming.addInstalledProduct(new ConsumerInstalledProductDTO(productC.getId(), productC.getName()));

        this.resource.updateConsumer(consumer.getUuid(), incoming, principal);
        verify(sink).queueEvent((Event) any());
    }

    @Test
    public void setStatusOnUpdate() throws Exception {
        Product productA = TestUtil.createProduct("Product A");
        Product productB = TestUtil.createProduct("Product B");
        Product productC = TestUtil.createProduct("Product C");

        Consumer consumer = getFakeConsumer();
        consumer.addInstalledProduct(new ConsumerInstalledProduct(consumer, productA));
        consumer.addInstalledProduct(new ConsumerInstalledProduct(consumer, productB));

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.addInstalledProduct(new ConsumerInstalledProductDTO(productB.getId(), productB.getName()));
        incoming.addInstalledProduct(new ConsumerInstalledProductDTO(productC.getId(), productC.getName()));

        this.resource.updateConsumer(consumer.getUuid(), incoming, principal);
        verify(sink).queueEvent((Event) any());
        verify(complianceRules).getStatus(eq(consumer), nullable(Date.class),
            any(Boolean.class), any(Boolean.class));
    }

    @Test
    public void testInstalledPackageSetEquality() throws Exception {
        Consumer consumerA = new Consumer();
        Consumer consumerB = new Consumer();
        Consumer consumerC = new Consumer();
        Consumer consumerD = new Consumer();

        Product productA = TestUtil.createProduct("Product A");
        Product productB = TestUtil.createProduct("Product B");
        Product productC = TestUtil.createProduct("Product C");
        Product productD = TestUtil.createProduct("Product D");

        consumerA.addInstalledProduct(new ConsumerInstalledProduct(consumerA, productA));
        consumerA.addInstalledProduct(new ConsumerInstalledProduct(consumerB, productB));
        consumerA.addInstalledProduct(new ConsumerInstalledProduct(consumerC, productC));

        consumerB.addInstalledProduct(new ConsumerInstalledProduct(consumerA, productA));
        consumerB.addInstalledProduct(new ConsumerInstalledProduct(consumerB, productB));
        consumerB.addInstalledProduct(new ConsumerInstalledProduct(consumerC, productC));

        consumerC.addInstalledProduct(new ConsumerInstalledProduct(consumerA, productA));
        consumerC.addInstalledProduct(new ConsumerInstalledProduct(consumerC, productC));

        consumerD.addInstalledProduct(new ConsumerInstalledProduct(consumerA, productA));
        consumerD.addInstalledProduct(new ConsumerInstalledProduct(consumerB, productB));
        consumerD.addInstalledProduct(new ConsumerInstalledProduct(consumerD, productD));

        assertEquals(consumerA.getInstalledProducts(), consumerB.getInstalledProducts());
        assertFalse(consumerA.getInstalledProducts().equals(consumerC.getInstalledProducts()));
        assertFalse(consumerA.getInstalledProducts().equals(consumerD.getInstalledProducts()));
    }

    @Test
    public void testGuestListEquality() throws Exception {
        Consumer a = new Consumer();
        a.addGuestId(new GuestId("Guest A"));
        a.addGuestId(new GuestId("Guest B"));
        a.addGuestId(new GuestId("Guest C"));

        Consumer b = new Consumer();
        b.addGuestId(new GuestId("Guest A"));
        b.addGuestId(new GuestId("Guest B"));
        b.addGuestId(new GuestId("Guest C"));

        Consumer c = new Consumer();
        c.addGuestId(new GuestId("Guest A"));
        c.addGuestId(new GuestId("Guest C"));

        Consumer d = new Consumer();
        d.addGuestId(new GuestId("Guest A"));
        d.addGuestId(new GuestId("Guest B"));
        d.addGuestId(new GuestId("Guest D"));

        assertEquals(a.getGuestIds(), b.getGuestIds());
        assertFalse(a.getGuestIds().equals(c.getGuestIds()));
        assertFalse(a.getGuestIds().equals(d.getGuestIds()));
    }

    @Test
    public void testUpdateConsumerUpdatesGuestIds() {
        String uuid = "TEST_CONSUMER";
        String[] existingGuests = new String[]{"Guest 1", "Guest 2", "Guest 3"};
        Consumer existing = createConsumerWithGuests(createOwner(), existingGuests);
        existing.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(existing);

        ConsumerDTO updated = new ConsumerDTO();
        updated.setUuid(uuid);

        GuestIdDTO expectedGuestId = TestUtil.createGuestIdDTO("Guest 2");
        updated.addGuestId(expectedGuestId);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(new VirtConsumerMap());
        this.resource.updateConsumer(existing.getUuid(), updated, principal);

        assertEquals(1, existing.getGuestIds().size());
        GuestId actualGID = existing.getGuestIds().iterator().next();
        assertNotNull(actualGID);
        assertEquals(actualGID.getGuestId(), expectedGuestId.getGuestId());
        assertEquals(actualGID.getAttributes(), expectedGuestId.getAttributes());
    }

    @Test
    public void testUpdateConsumerDoesNotChangeGuestsWhenGuestIdsNotIncludedInRequest() {
        String uuid = "TEST_CONSUMER";
        String[] guests = new String[]{ "Guest 1", "Guest 2" };
        Consumer existing = createConsumerWithGuests(createOwner(), guests);
        existing.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(existing);

        ConsumerDTO updated = new ConsumerDTO();
        this.resource.updateConsumer(existing.getUuid(), updated, principal);
        assertEquals(guests.length, existing.getGuestIds().size());
    }

    @Test
    public void testUpdateConsumerClearsGuestListWhenRequestGuestListIsEmptyButNotNull() {
        String uuid = "TEST_CONSUMER";
        String[] guests = new String[]{ "Guest 1", "Guest 2" };
        Consumer existing = createConsumerWithGuests(createOwner(), guests);
        existing.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(existing);

        ConsumerDTO updated = new ConsumerDTO();
        updated.setGuestIds(new ArrayList<>());
        this.resource.updateConsumer(existing.getUuid(), updated, principal);
        assertTrue(existing.getGuestIds().isEmpty());
    }

    @Test
    public void ensureEventIsNotFiredWhenNoChangeWasMadeToConsumerGuestIds() {
        String uuid = "TEST_CONSUMER";
        Consumer existing = createConsumerWithGuests(createOwner(), "Guest 1", "Guest 2");
        existing.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(existing);

        ConsumerDTO updated = createConsumerDTOWithGuests("Guest 1", "Guest 2");
        updated.setUuid(uuid);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(new VirtConsumerMap());

        this.resource.updateConsumer(existing.getUuid(), updated, principal);
        verify(sink, never()).queueEvent(any(Event.class));
    }

    @Test
    public void ensureEventIsNotFiredWhenGuestIDCaseChanges() {
        String uuid = "TEST_CONSUMER";
        Consumer existing = createConsumerWithGuests(createOwner(), "aaa123", "bbb123");
        existing.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(existing);

        // flip case on one ID, should be treated as no change
        ConsumerDTO updated = createConsumerDTOWithGuests("aaa123", "BBB123");
        updated.setUuid(uuid);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(new VirtConsumerMap());

        this.resource.updateConsumer(existing.getUuid(), updated, principal);
        verify(sink, never()).queueEvent(any(Event.class));
    }

    // ignored out per mkhusid, see 768872 comment #41
    @Disabled
    @Test
    public void ensureNewGuestIsHealedIfItWasMigratedFromAnotherHost() throws Exception {
        String uuid = "TEST_CONSUMER";
        Owner owner = createOwner();
        Consumer existingHost = createConsumerWithGuests(owner, "Guest 1", "Guest 2");
        existingHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "1");
        entitlement.getPool().setAttribute(Pool.Attributes.REQUIRES_HOST, uuid);

        Consumer guest1 = new Consumer().setUuid("Guest 1");
        guest1.addEntitlement(entitlement);
        ConsumerInstalledProduct installed = mock(ConsumerInstalledProduct.class);
        guest1.addInstalledProduct(installed);

        when(consumerCurator.findByVirtUuid("Guest 1",
            owner.getId())).thenReturn(guest1);
        // Ensure that the guests host is the existing.
        when(consumerCurator.getHost("Guest 1",
            owner.getId())).thenReturn(existingHost);
        when(consumerCurator.findByUuid("Guest 1")).thenReturn(guest1);

        Consumer existingMigratedTo = createConsumerWithGuests(owner);
        existingMigratedTo.setUuid("MIGRATED_TO");
        when(this.consumerCurator.findByUuid(existingMigratedTo.getUuid()))
            .thenReturn(existingMigratedTo);

        this.resource.updateConsumer(
            existingMigratedTo.getUuid(),
            createConsumerDTOWithGuests("Guest 1"),
            principal
        );

        verify(poolManager).revokeEntitlement(eq(entitlement));
        verify(entitler).bindByProducts(AutobindData.create(guest1, owner));
    }

    @Test
    public void ensureExistingGuestHasEntitlementIsRemovedIfAlreadyAssocWithDiffHost() {
        // the guest in this test does not have any installed products, we
        // expect them to get their entitlements stripped on migration
        String uuid = "TEST_CONSUMER";
        Consumer existingHost = createConsumerWithGuests(createOwner(), "Guest 1", "Guest 2");
        existingHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "1");
        entitlement.getPool().setAttribute(Pool.Attributes.REQUIRES_HOST, uuid);

        Consumer guest1 = new Consumer().setUuid("Guest 1");
        guest1.addEntitlement(entitlement);
        guest1.setAutoheal(true);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(mockVirtConsumerMap("Guest 1", guest1));

        // Ensure that the guests host is the existing.

        Consumer existingMigratedTo = createConsumerWithGuests(createOwner(), "Guest 1");
        existingMigratedTo.setUuid("MIGRATED_TO");
        when(this.consumerCurator.verifyAndLookupConsumer(existingMigratedTo.getUuid()))
            .thenReturn(existingMigratedTo);
        when(this.consumerCurator.get(eq(guest1.getId()))).thenReturn(guest1);

        this.resource.updateConsumer(
            existingMigratedTo.getUuid(),
            createConsumerDTOWithGuests("Guest 1"),
            principal
        );
    }

    @Test
    public void ensureGuestEntitlementsUntouchedWhenGuestIsNewWithNoOtherHost() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests(createOwner());
        host.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(host);

        ConsumerDTO updatedHost = createConsumerDTOWithGuests("Guest 1");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "1");
        entitlement.getPool().setAttribute(Pool.Attributes.REQUIRES_HOST, uuid);

        Consumer guest1 = new Consumer().setUuid("Guest 1");
        guest1.addEntitlement(entitlement);
        guest1.setAutoheal(true);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(mockVirtConsumerMap("Guest 1", guest1));
        // Ensure that the guest was not reported by another host.
        when(this.consumerCurator.get(eq(guest1.getId()))).thenReturn(guest1);

        this.resource.updateConsumer(host.getUuid(), updatedHost, principal);
        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }

    @Test
    public void ensureGuestEntitlementsUntouchedWhenGuestExistsWithNoOtherHost() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests(createOwner(), "Guest 1");
        host.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(host);

        ConsumerDTO updatedHost = createConsumerDTOWithGuests("Guest 1");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "1");
        entitlement.getPool().setAttribute(Pool.Attributes.REQUIRES_HOST, uuid);

        Consumer guest1 = new Consumer().setUuid("Guest 1");
        guest1.addEntitlement(entitlement);

        // Ensure that the guest was already reported by same host.
        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(mockVirtConsumerMap("Guest 1", guest1));
        this.resource.updateConsumer(host.getUuid(), updatedHost, principal);
        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }

    @Test
    public void ensureGuestEntitlementsAreNotRevokedWhenGuestIsRemovedFromHost() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests(createOwner(), "Guest 1", "Guest 2");
        host.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(host);

        ConsumerDTO updatedHost = createConsumerDTOWithGuests("Guest 2");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "1");
        entitlement.getPool().setAttribute(Pool.Attributes.REQUIRES_HOST, uuid);

        Consumer guest1 = new Consumer();
        guest1.setUuid("Guest 1");
        guest1.addEntitlement(entitlement);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(mockVirtConsumerMap("Guest 1", guest1));

        this.resource.updateConsumer(host.getUuid(), updatedHost, principal);
        //verify(consumerCurator).findByVirtUuid(eq("Guest 1"));
        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }

    private VirtConsumerMap mockVirtConsumerMap(String uuid, Consumer consumer) {
        VirtConsumerMap map = new VirtConsumerMap();
        map.add(uuid, consumer);
        return map;
    }

    @Test
    public void ensureGuestEntitlementsAreNotRemovedWhenGuestsAndHostAreTheSame() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests(createOwner(), "Guest 1");
        host.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(host);

        ConsumerDTO updatedHost = createConsumerDTOWithGuests("Guest 1");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "1");
        entitlement.getPool().setAttribute(Pool.Attributes.REQUIRES_HOST, uuid);

        Consumer guest1 = new Consumer();
        guest1.setUuid("Guest 1");
        guest1.addEntitlement(entitlement);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(mockVirtConsumerMap("Guest 1", guest1));

        this.resource.updateConsumer(host.getUuid(), updatedHost, principal);

        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }

    @Test
    public void guestEntitlementsNotRemovedIfEntitlementIsVirtOnlyButRequiresHostNotSet() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests(createOwner(), "Guest 1", "Guest 2");
        host.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(host);

        ConsumerDTO updatedHost = createConsumerDTOWithGuests("Guest 1");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "1");

        Consumer guest1 = new Consumer().setUuid("Guest 1");
        guest1.addEntitlement(entitlement);
        guest1.setAutoheal(true);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(mockVirtConsumerMap("Guest 1", guest1));
        when(this.consumerCurator.get(eq(guest1.getId()))).thenReturn(guest1);

        this.resource.updateConsumer(host.getUuid(), updatedHost, principal);

        //verify(consumerCurator).findByVirtUuid(eq("Guest 1"));
        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }

    @Test
    public void multipleUpdatesCanOccur() {
        String uuid = "A Consumer";
        String expectedFactName = "FACT1";
        String expectedFactValue = "F1";
        GuestIdDTO expectedGuestId = TestUtil.createGuestIdDTO("GUEST_ID_1");

        Consumer existing = getFakeConsumer();
        existing.setFacts(new HashMap<>());
        existing.setInstalledProducts(new HashSet<>());

        ConsumerDTO updated = new ConsumerDTO();
        updated.setUuid(uuid);
        updated.setFact(expectedFactName, expectedFactValue);
        Product prod = TestUtil.createProduct("Product One");
        ConsumerInstalledProductDTO expectedInstalledProduct =
            new ConsumerInstalledProductDTO(prod.getId(), prod.getName());

        updated.addInstalledProduct(expectedInstalledProduct);
        updated.addGuestId(expectedGuestId);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), any(Set.class))).
            thenReturn(new VirtConsumerMap());
        this.resource.updateConsumer(existing.getUuid(), updated, principal);

        assertEquals(1, existing.getFacts().size());
        assertEquals(expectedFactValue, existing.getFact(expectedFactName));

        assertEquals(1, existing.getInstalledProducts().size());
        ConsumerInstalledProduct actualCIP = existing.getInstalledProducts().iterator().next();
        assertNotNull(actualCIP);
        assertEquals(actualCIP.getProductId(), expectedInstalledProduct.getProductId());
        assertEquals(actualCIP.getProductName(), expectedInstalledProduct.getProductName());
        assertEquals(actualCIP.getVersion(), expectedInstalledProduct.getVersion());
        assertEquals(actualCIP.getArch(), expectedInstalledProduct.getArch());
        assertEquals(actualCIP.getStatus(), expectedInstalledProduct.getStatus());
        assertEquals(actualCIP.getStartDate(), expectedInstalledProduct.getStartDate());
        assertEquals(actualCIP.getEndDate(), expectedInstalledProduct.getEndDate());

        assertEquals(1, existing.getGuestIds().size());
        GuestId actualGID = existing.getGuestIds().iterator().next();
        assertNotNull(actualGID);
        assertEquals(actualGID.getGuestId(), expectedGuestId.getGuestId());
        assertEquals(actualGID.getAttributes(), expectedGuestId.getAttributes());
    }

    @Test
    public void canUpdateConsumerEnvironment() {
        Environment changedEnvironment = new Environment("42", "environment", null);

        Consumer existing = getFakeConsumer();

        ConsumerDTO updated = new ConsumerDTO();
        Owner owner = createOwner();
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setId(owner.getId());
        updated.setEnvironment(translator.translate(changedEnvironment, EnvironmentDTO.class));
        updated.setOwner(new OwnerDTO());

        when(environmentCurator.get(changedEnvironment.getId())).thenReturn(changedEnvironment);
        when(ownerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);
        when(environmentCurator.exists(changedEnvironment.getId())).thenReturn(true);

        resource.updateConsumer(existing.getUuid(), updated, principal);

        verify(poolManager, atMost(1)).regenerateCertificatesOf(existing, true);
        verify(sink).queueEvent((Event) any());
    }

    @Test
    public void throwsAnExceptionWhenEnvironmentNotFound() {
        String uuid = "A Consumer";
        EnvironmentDTO changedEnvironment = new EnvironmentDTO()
            .setId("42")
            .setName("environment");

        ConsumerDTO updated = new ConsumerDTO();
        updated.setUuid(uuid);
        updated.setEnvironment(changedEnvironment);

        Consumer existing = getFakeConsumer();
        existing.setUuid(updated.getUuid());

        when(consumerCurator.verifyAndLookupConsumer(existing.getUuid())).thenReturn(existing);
        when(environmentCurator.get(changedEnvironment.getId())).thenReturn(null);

        assertThrows(NotFoundException.class,
            () -> resource.updateConsumer(existing.getUuid(), updated, principal));
    }

    @Test
    public void canUpdateName() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName("new name");

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        resource.updateConsumer(consumer.getUuid(), updated, principal);
        verify(consumerCurator, times(1)).update(consumerCaptor.capture());

        Consumer mergedConsumer = consumerCaptor.getValue();
        assertEquals(updated.getName(), mergedConsumer.getName());
    }

    @Test
    public void updatedNameRegeneratesIdCert() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName("new name");

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        resource.updateConsumer(consumer.getUuid(), updated, principal);
        verify(consumerCurator, times(1)).update(consumerCaptor.capture());
        Consumer mergedConsumer = consumerCaptor.getValue();

        assertEquals(updated.getName(), mergedConsumer.getName());
        assertNotNull(mergedConsumer.getIdCert());
    }

    @Test
    public void sameNameDoesntRegenIdCert() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setName("old name");
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName("old name");

        resource.updateConsumer(consumer.getUuid(), updated, principal);

        assertEquals(updated.getName(), consumer.getName());
        assertNull(consumer.getIdCertificate());
    }

    @Test
    public void updatingToNullNameIgnoresName() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setName("old name");
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName(null);

        resource.updateConsumer(consumer.getUuid(), updated, principal);
        assertEquals("old name", consumer.getName());
    }

    @Test
    public void updatingToInvalidCharacterNameNotAllowed() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setName("old name");
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName("#a name");

        assertThrows(BadRequestException.class,
            () -> resource.updateConsumer(consumer.getUuid(), updated, principal));
    }

    @Test
    public void consumerCapabilityUpdate() {
        ConsumerType ct = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        this.mockConsumerType(ct);

        Consumer c = getFakeConsumer();
        Set<ConsumerCapability> caps = new HashSet<>();
        ConsumerCapability cca = new ConsumerCapability(c, "capability_a");
        ConsumerCapability ccb = new ConsumerCapability(c, "capability_b");
        ConsumerCapability ccc = new ConsumerCapability(c, "capability_c");
        caps.add(cca);
        caps.add(ccb);
        caps.add(ccc);
        c.setCapabilities(caps);
        c.setType(ct);
        assertEquals(3, c.getCapabilities().size());

        // no capability list in update object does not change existing
        // also shows that setCapabilites can accept null and not error
        ConsumerDTO updated = new ConsumerDTO();
        updated.setCapabilities(null);
        resource.updateConsumer(c.getUuid(), updated, principal);
        assertEquals(3, c.getCapabilities().size());

        // empty capability list in update object does change existing
        updated = new ConsumerDTO();
        updated.setCapabilities(new HashSet<>());
        resource.updateConsumer(c.getUuid(), updated, principal);
        assertEquals(0, c.getCapabilities().size());
    }

    @Test
    public void consumerChangeDetection() {
        Consumer existing = getFakeConsumer();
        Set<ConsumerCapability> caps1 = new HashSet<>();
        Set<ConsumerCapability> caps2 = new HashSet<>();
        ConsumerCapability cca = new ConsumerCapability(existing, "capability_a");
        ConsumerCapability ccb = new ConsumerCapability(existing, "capability_b");
        ConsumerCapability ccc = new ConsumerCapability(existing, "capability_c");
        caps1.add(cca);
        caps1.add(ccb);
        caps1.add(ccc);
        caps2.add(ccb);

        existing.setCapabilities(caps1);

        Consumer update = getFakeConsumer();
        update.setCapabilities(caps1);
        assertFalse(resource.performConsumerUpdates(
            this.translator.translate(update, ConsumerDTO.class), existing, testMigration));

        update.setCapabilities(caps2);
        assertTrue(resource.performConsumerUpdates(
            this.translator.translate(update, ConsumerDTO.class), existing, testMigration));

        // need a new consumer here, can't null out capabilities
        update = getFakeConsumer();
        assertFalse(resource.performConsumerUpdates(
            this.translator.translate(update, ConsumerDTO.class), existing, testMigration));
    }

    @Test
    public void consumerLastCheckin() {
        ConsumerType ct = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        this.mockConsumerType(ct);

        Consumer c = getFakeConsumer();
        Date now = new Date();
        c.setLastCheckin(now);
        c.setType(ct);

        when(this.consumerCurator.verifyAndLookupConsumer(c.getUuid())).thenReturn(c);

        ConsumerDTO updated = new ConsumerDTO();
        Date then = new Date(now.getTime() + 10000L);
        updated.setLastCheckin(then);
        resource.updateConsumer(c.getUuid(), updated, principal);
    }

    private Owner createOwner() {
        Owner owner = new Owner();
        owner.setId("FAKEOWNERID");
        return owner;
    }

    private Consumer createConsumerWithGuests(Owner owner, String ... guestIds) {
        Consumer a = new Consumer();
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.HYPERVISOR);
        this.mockConsumerType(ctype);
        a.setType(ctype);
        a.setOwner(owner);
        for (String guestId : guestIds) {
            a.addGuestId(new GuestId(guestId));
        }
        return a;
    }

    private ConsumerDTO createConsumerDTOWithGuests(String ... guestIds) {
        Consumer consumer = createConsumerWithGuests(createOwner(), guestIds);
        // re-add guestIds as consumer translator removes them.
        List<GuestIdDTO> guestIdDTOS = new LinkedList<>();
        for (GuestId guestId : consumer.getGuestIds()) {
            guestIdDTOS.add(translator.translate(guestId, GuestIdDTO.class));
        }
        return translator.translate(consumer, ConsumerDTO.class).setGuestIds(guestIdDTOS);
    }
}
