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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

import org.candlepin.async.JobManager;
import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.EntitlementCertificateService;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.server.v1.CapabilityDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.server.v1.EnvironmentDTO;
import org.candlepin.dto.api.server.v1.GuestIdDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.OwnerDTO;
import org.candlepin.dto.api.server.v1.ReleaseVerDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCloudData;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.pki.certs.AnonymousCertificateGenerator;
import org.candlepin.pki.certs.IdentityCertificateGenerator;
import org.candlepin.pki.certs.SCACertificateGenerator;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.compliance.hash.ComplianceFacts;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerCloudDataBuilder;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.FactValidator;
import org.candlepin.util.Util;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;


/**
 * Test suite for the ConsumerResource class, focusing on updates
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsumerResourceUpdateTest {

    @Mock
    private IdentityCertificateGenerator identityCertificateGenerator;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private EventSink sink;
    @Mock
    private EventFactory eventFactory;
    @Mock
    private ActivationKeyCurator activationKeyCurator;
    @Mock
    private PoolManager poolManager;
    @Mock
    private PoolService poolService;
    @Mock
    private RefresherFactory refresherFactory;
    @Mock
    private ComplianceRules complianceRules;
    @Mock
    private SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock
    private Entitler entitler;
    @Mock
    private DeletedConsumerCurator deletedConsumerCurator;
    @Mock
    private EnvironmentCurator environmentCurator;
    @Mock
    private EventBuilder consumerEventBuilder;
    @Mock
    private ConsumerBindUtil consumerBindUtil;
    @Mock
    private ConsumerEnricher consumerEnricher;
    @Mock
    private JobManager jobManager;
    @Mock
    private DTOValidator dtoValidator;
    @Mock
    private EntitlementCertServiceAdapter entitlementCertServiceAdapter;
    @Mock
    private SubscriptionServiceAdapter subscriptionServiceAdapter;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private ContentAccessManager contentAccessManager;
    @Mock
    private ManifestManager manifestManager;
    @Mock
    private UserServiceAdapter userServiceAdapter;
    @Mock
    private ConsumerRules consumerRules;
    @Mock
    private CalculatedAttributesUtil calculatedAttributesUtil;
    @Mock
    private DistributorVersionCurator distributorVersionCurator;
    @Mock
    private PrincipalProvider principalProvider;
    @Mock
    private ModelTranslator modelTranslator;
    @Mock
    private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    @Mock
    private ContentOverrideValidator contentOverrideValidator;
    @Mock
    private EnvironmentContentCurator environmentContentCurator;
    @Mock
    private EntitlementCertificateService entCertService;
    @Mock
    private PoolCurator poolCurator;
    @Mock
    private CloudRegistrationAdapter cloudRegistrationAdapter;
    @Mock
    private AnonymousCloudConsumerCurator anonymousConsumerCurator;
    @Mock
    private AnonymousContentAccessCertificateCurator anonymousCertCurator;
    @Mock
    private OwnerServiceAdapter ownerService;
    @Mock
    private SCACertificateGenerator scaCertificateGenerator;
    @Mock
    private AnonymousCertificateGenerator anonymousCertificateGenerator;
    @Mock
    private ConsumerCloudDataBuilder cloudDataBuilder;

    private ModelTranslator translator;

    private I18n i18n;

    private ConsumerResource resource;
    private GuestMigration testMigration;
    private Configuration config;

    @BeforeEach
    public void init() throws Exception {
        this.config = TestConfig.defaults();

        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.testMigration = new GuestMigration(this.consumerCurator);

        this.translator = new StandardTranslator(this.consumerTypeCurator, this.environmentCurator,
            this.ownerCurator);

        this.resource = new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionServiceAdapter,
            this.entitlementCurator,
            this.identityCertificateGenerator,
            this.entitlementCertServiceAdapter,
            this.i18n,
            this.sink,
            this.eventFactory,
            this.userServiceAdapter,
            this.poolManager,
            this.refresherFactory,
            this.consumerRules,
            this.ownerCurator,
            this.activationKeyCurator,
            this.entitler,
            this.complianceRules,
            this.systemPurposeComplianceRules,
            this.deletedConsumerCurator,
            this.environmentCurator,
            this.distributorVersionCurator,
            this.config,
            this.calculatedAttributesUtil,
            this.consumerBindUtil,
            this.manifestManager,
            this.contentAccessManager,
            new FactValidator(this.config, () -> this.i18n),
            new ConsumerTypeValidator(consumerTypeCurator, i18n),
            this.consumerEnricher,
            Providers.of(this.testMigration),
            this.modelTranslator,
            this.jobManager,
            this.dtoValidator,
            this.principalProvider,
            this.contentOverrideValidator,
            this.consumerContentOverrideCurator,
            this.entCertService,
            this.poolService,
            this.environmentContentCurator,
            this.anonymousConsumerCurator,
            this.anonymousCertCurator,
            this.ownerService,
            this.scaCertificateGenerator,
            this.anonymousCertificateGenerator,
            this.cloudDataBuilder
        );

        when(this.complianceRules.getStatus(any(Consumer.class), any(Date.class), any(Boolean.class),
            any(Boolean.class))).thenReturn(new ComplianceStatus(new Date()));

        when(this.identityCertificateGenerator.regenerate(any(Consumer.class)))
            .thenReturn(new IdentityCertificate());

        when(this.consumerEventBuilder.setEventData(any(Consumer.class)))
            .thenReturn(this.consumerEventBuilder);
        when(this.eventFactory.getEventBuilder(any(Target.class), any(Type.class)))
            .thenReturn(this.consumerEventBuilder);
    }

    protected ConsumerType mockConsumerType(ConsumerType ctype) {
        if (ctype != null) {
            // Ensure the type has an ID
            if (ctype.getId() == null) {
                ctype.setId("test-ctype-" + ctype.getLabel() + "-" + TestUtil.randomInt());
            }

            when(this.consumerTypeCurator.getByLabel(ctype.getLabel())).thenReturn(ctype);
            when(this.consumerTypeCurator.getByLabel(eq(ctype.getLabel()), anyBoolean())).thenReturn(ctype);
            when(this.consumerTypeCurator.get(ctype.getId())).thenReturn(ctype);

            doAnswer(new Answer<ConsumerType>() {
                @Override
                public ConsumerType answer(InvocationOnMock invocation) {
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
    public void nothingChanged() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(), consumer);
        verify(sink, never()).queueEvent(any());
    }

    private Owner mockOwner(String id) {
        Owner owner = new Owner()
            .setId(id)
            .setKey(id);

        doReturn(owner).when(this.ownerCurator).findOwnerById(owner.getId());

        return owner;
    }

    private Owner createOwner() {
        return this.mockOwner("FAKEOWNERID");
    }

    private Consumer mockConsumer(Owner owner) {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        this.mockConsumerType(ctype);

        Consumer consumer = new Consumer()
            .setUuid("FAKEUUID")
            .setOwner(owner)
            .setName("FAKENAME")
            .setType(ctype);

        doReturn(consumer).when(this.consumerCurator).findByUuid(consumer.getUuid());
        doReturn(consumer).when(this.consumerCurator).verifyAndLookupConsumer(consumer.getUuid());

        return consumer;
    }

    private Consumer getFakeConsumer() {
        return this.mockConsumer(this.createOwner());
    }

    private ConsumerDTO getFakeConsumerDTO() {
        return translator.translate(getFakeConsumer(), ConsumerDTO.class);
    }

    private Environment mockEnvironment(Owner owner, String id, String name) {
        Environment environment = new Environment()
            .setId(id)
            .setName(name)
            .setOwner(owner);

        doReturn(environment).when(this.environmentCurator).get(environment.getId());
        doReturn(true).when(this.environmentCurator).exists(environment.getId());

        return environment;
    }

    @Test
    public void allowNameWithUnderscore() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("test_user"));
    }

    @Test
    public void allowNameWithCamelCase() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("ConsumerTest32953"));
    }

    @Test
    public void allowNameThatStartsWithUnderscore() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("__init__"));
    }

    @Test
    public void allowNameThatStartsWithDash() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("-dash"));
    }

    @Test
    public void allowNameThatContainsNumbers() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("testmachine99"));
    }

    @Test
    public void allowNameThatStartsWithNumbers() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("001test7"));
    }

    @Test
    public void allowNameThatContainsPeriods() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("test-system.resource.net"));
    }

    @Test
    public void allowNameThatContainsUserServiceChars() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        this.resource.updateConsumer(consumer.getUuid(),
            new ConsumerDTO().name("{bob}'s_b!g_#boi.`?uestlove!x"));
    }

    // These should receive an error with the default consumer name pattern
    @Test
    public void disallowNameThatContainsMultibyteKorean() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        assertThrows(BadRequestException.class,
            () -> this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("서브스크립션")));
    }

    @Test
    public void disallowNameThatContainsMultibyteOriya() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        assertThrows(BadRequestException.class,
            () -> this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("ପରିବେଶ")));
    }

    @Test
    public void disallowEmptyConsumerName() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        assertThrows(BadRequestException.class,
            () -> this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("")));
    }

    @Test
    public void disallowNameThatContainsWhitespace() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        assertThrows(BadRequestException.class,
            () -> this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("abc123 456")));
    }

    @Test
    public void disallowNameThatStartsWithBadCharacter() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        assertThrows(BadRequestException.class,
            () -> this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("<foo")));
    }

    @Test
    public void disallowNameThatContainsBadCharacter() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        assertThrows(BadRequestException.class,
            () -> this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("bar$%camp")));
    }

    /*
     * This is a special case. We should never allow a name starting with pound,
     * regardless of the regex config.
     */
    @Test
    public void disallowNameThatStartsWithPound() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        assertThrows(BadRequestException.class,
            () -> this.resource.updateConsumer(consumer.getUuid(), new ConsumerDTO().name("#pound")));
    }

    @Test
    public void testUpdatesOnContentTagChanges() {
        HashSet<String> originalTags = new HashSet<>(Arrays.asList("hello", "world"));
        HashSet<String> changedTags = new HashSet<>(Arrays.asList("x", "y"));

        ConsumerDTO c = getFakeConsumerDTO();
        c.setContentTags(originalTags);

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.setContentTags(changedTags);

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        resource.updateConsumer(c.getUuid(), incoming);
        verify(consumerCurator, times(1)).update(consumerCaptor.capture());
        Consumer mergedConsumer = consumerCaptor.getValue();

        assertEquals(changedTags, mergedConsumer.getContentTags());
    }

    @Test
    public void nullReleaseVer() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setReleaseVer(null);

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.setReleaseVer(new ReleaseVerDTO().releaseVer("not null"));
        this.resource.updateConsumer(consumer.getUuid(), incoming);

        ConsumerDTO consumer2 = getFakeConsumerDTO();
        consumer2.setReleaseVer(new ReleaseVerDTO().releaseVer("foo"));
        ConsumerDTO incoming2 = new ConsumerDTO();
        incoming2.setReleaseVer(null);
        this.resource.updateConsumer(consumer2.getUuid(), incoming2);
    }

    private void compareConsumerRelease(String release1, String release2, Boolean verify) {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer(release1));

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.setReleaseVer(new ReleaseVerDTO().releaseVer(release2));

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        this.resource.updateConsumer(consumer.getUuid(), incoming);
        verify(consumerCurator, times(1)).update(consumerCaptor.capture());
        Consumer mergedConsumer = consumerCaptor.getValue();
        if (verify) {
            verify(sink).queueEvent(any());
        }
        assertEquals(incoming.getReleaseVer().getReleaseVer(),
            mergedConsumer.getReleaseVer().getReleaseVer());
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
    public void installedPackagesChanged() {
        Product productA = TestUtil.createProduct("Product A");
        Product productB = TestUtil.createProduct("Product B");
        Product productC = TestUtil.createProduct("Product C");

        Consumer consumer = getFakeConsumer();
        consumer.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productA.getId())
            .setProductName(productA.getName()));
        consumer.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productB.getId())
            .setProductName(productB.getName()));

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.addInstalledProductsItem(new ConsumerInstalledProductDTO()
            .id(productB.getId())
            .productName(productB.getName()));
        incoming.addInstalledProductsItem(new ConsumerInstalledProductDTO()
            .id(productC.getId())
            .productName(productC.getName()));

        this.resource.updateConsumer(consumer.getUuid(), incoming);
        verify(sink).queueEvent(any());
    }

    @Test
    public void setStatusOnUpdate() {
        Product productA = TestUtil.createProduct("Product A");
        Product productB = TestUtil.createProduct("Product B");
        Product productC = TestUtil.createProduct("Product C");

        Consumer consumer = getFakeConsumer();
        consumer.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productA.getId())
            .setProductName(productA.getName()));
        consumer.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productB.getId())
            .setProductName(productB.getName()));

        ConsumerDTO incoming = new ConsumerDTO();
        incoming.addInstalledProductsItem(new ConsumerInstalledProductDTO()
            .id(productB.getId())
            .productName(productB.getName()));
        incoming.addInstalledProductsItem(new ConsumerInstalledProductDTO()
            .id(productC.getId())
            .productName(productC.getName()));

        this.resource.updateConsumer(consumer.getUuid(), incoming);
        verify(sink).queueEvent(any());
        verify(complianceRules).getStatus(eq(consumer), nullable(Date.class),
            any(Boolean.class), any(Boolean.class));
    }

    @Test
    public void testInstalledPackageSetEquality() {
        Consumer consumerA = new Consumer();
        Consumer consumerB = new Consumer();
        Consumer consumerC = new Consumer();
        Consumer consumerD = new Consumer();

        Product productA = TestUtil.createProduct("Product A");
        Product productB = TestUtil.createProduct("Product B");
        Product productC = TestUtil.createProduct("Product C");
        Product productD = TestUtil.createProduct("Product D");

        consumerA.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productA.getId())
            .setProductName(productA.getName()));
        consumerA.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productB.getId())
            .setProductName(productB.getName()));
        consumerA.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productC.getId())
            .setProductName(productC.getName()));

        consumerB.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productA.getId())
            .setProductName(productA.getName()));
        consumerB.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productB.getId())
            .setProductName(productB.getName()));
        consumerB.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productC.getId())
            .setProductName(productC.getName()));

        consumerC.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productA.getId())
            .setProductName(productA.getName()));
        consumerC.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productC.getId())
            .setProductName(productC.getName()));

        consumerD.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productA.getId())
            .setProductName(productA.getName()));
        consumerD.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productB.getId())
            .setProductName(productB.getName()));
        consumerD.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(productD.getId())
            .setProductName(productD.getName()));

        assertEquals(consumerA.getInstalledProducts(), consumerB.getInstalledProducts());
        assertNotEquals(consumerC.getInstalledProducts(), consumerA.getInstalledProducts());
        assertNotEquals(consumerD.getInstalledProducts(), consumerA.getInstalledProducts());
    }

    @Test
    public void testGuestListEquality() {
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
        assertNotEquals(c.getGuestIds(), a.getGuestIds());
        assertNotEquals(d.getGuestIds(), a.getGuestIds());
    }

    @Test
    public void testUpdateConsumerUpdatesGuestIds() {
        String uuid = "TEST_CONSUMER";
        String[] existingGuests = new String[] { "Guest 1", "Guest 2", "Guest 3" };
        Consumer existing = createConsumerWithGuests(createOwner(), existingGuests);
        existing.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(existing);

        ConsumerDTO updated = new ConsumerDTO();
        updated.setUuid(uuid);

        GuestIdDTO expectedGuestId = TestUtil.createGuestIdDTO("Guest 2");
        updated.addGuestIdsItem(expectedGuestId);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(new VirtConsumerMap());
        this.resource.updateConsumer(existing.getUuid(), updated);

        assertEquals(1, existing.getGuestIds().size());
        GuestId actualGID = existing.getGuestIds().iterator().next();
        assertNotNull(actualGID);
        assertEquals(actualGID.getGuestId(), expectedGuestId.getGuestId());
        assertEquals(actualGID.getAttributes(), expectedGuestId.getAttributes());
    }

    @Test
    public void testUpdateConsumerDoesNotChangeGuestsWhenGuestIdsNotIncludedInRequest() {
        String uuid = "TEST_CONSUMER";
        String[] guests = new String[] { "Guest 1", "Guest 2" };
        Consumer existing = createConsumerWithGuests(createOwner(), guests);
        existing.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(existing);

        ConsumerDTO updated = new ConsumerDTO();
        this.resource.updateConsumer(existing.getUuid(), updated);
        assertEquals(guests.length, existing.getGuestIds().size());
    }

    @Test
    public void testUpdateConsumerClearsGuestListWhenRequestGuestListIsEmptyButNotNull() {
        String uuid = "TEST_CONSUMER";
        String[] guests = new String[] { "Guest 1", "Guest 2" };
        Consumer existing = createConsumerWithGuests(createOwner(), guests);
        existing.setUuid(uuid);

        when(this.consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(existing);

        ConsumerDTO updated = new ConsumerDTO();
        updated.setGuestIds(new ArrayList<>());
        this.resource.updateConsumer(existing.getUuid(), updated);
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

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(new VirtConsumerMap());

        this.resource.updateConsumer(existing.getUuid(), updated);
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

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(new VirtConsumerMap());

        this.resource.updateConsumer(existing.getUuid(), updated);
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
            createConsumerDTOWithGuests("Guest 1"));

        verify(this.poolService).revokeEntitlement(entitlement);
        verify(entitler).bindByProducts(new AutobindData(guest1, owner));
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

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(mockVirtConsumerMap("Guest 1", guest1));

        // Ensure that the guests host is the existing.

        Consumer existingMigratedTo = createConsumerWithGuests(createOwner(), "Guest 1");
        existingMigratedTo.setUuid("MIGRATED_TO");
        when(this.consumerCurator.verifyAndLookupConsumer(existingMigratedTo.getUuid()))
            .thenReturn(existingMigratedTo);
        when(this.consumerCurator.get(guest1.getId())).thenReturn(guest1);

        this.resource.updateConsumer(
            existingMigratedTo.getUuid(),
            createConsumerDTOWithGuests("Guest 1"));
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

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(mockVirtConsumerMap("Guest 1", guest1));
        // Ensure that the guest was not reported by another host.
        when(this.consumerCurator.get(guest1.getId())).thenReturn(guest1);

        this.resource.updateConsumer(host.getUuid(), updatedHost);
        verify(poolService, never()).revokeEntitlement(entitlement);
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
        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(mockVirtConsumerMap("Guest 1", guest1));
        this.resource.updateConsumer(host.getUuid(), updatedHost);
        verify(poolService, never()).revokeEntitlement(entitlement);
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

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(mockVirtConsumerMap("Guest 1", guest1));

        this.resource.updateConsumer(host.getUuid(), updatedHost);
        // verify(consumerCurator).findByVirtUuid(eq("Guest 1"));
        verify(poolService, never()).revokeEntitlement(entitlement);
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

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(mockVirtConsumerMap("Guest 1", guest1));

        this.resource.updateConsumer(host.getUuid(), updatedHost);

        verify(poolService, never()).revokeEntitlement(entitlement);
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

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(mockVirtConsumerMap("Guest 1", guest1));
        when(this.consumerCurator.get(guest1.getId())).thenReturn(guest1);

        this.resource.updateConsumer(host.getUuid(), updatedHost);

        // verify(consumerCurator).findByVirtUuid(eq("Guest 1"));
        verify(poolService, never()).revokeEntitlement(entitlement);
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
        updated.putFactsItem(expectedFactName, expectedFactValue);
        Product prod = TestUtil.createProduct("Product One");
        ConsumerInstalledProductDTO expectedInstalledProduct = new ConsumerInstalledProductDTO()
            .id(prod.getId()).productName(prod.getName());

        updated.addInstalledProductsItem(expectedInstalledProduct);
        updated.addGuestIdsItem(expectedGuestId);

        when(this.consumerCurator.getGuestConsumersMap(any(String.class), anySet()))
            .thenReturn(new VirtConsumerMap());
        this.resource.updateConsumer(existing.getUuid(), updated);

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
        assertEquals(Util.toDateTime(actualCIP.getStartDate()), expectedInstalledProduct.getStartDate());
        assertEquals(Util.toDateTime(actualCIP.getEndDate()), expectedInstalledProduct.getEndDate());

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
        updated.addEnvironmentsItem(translator.translate(changedEnvironment, EnvironmentDTO.class));
        updated.setOwner(new NestedOwnerDTO());

        when(environmentCurator.get(changedEnvironment.getId())).thenReturn(changedEnvironment);
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);
        when(environmentCurator.exists(changedEnvironment.getId())).thenReturn(true);

        resource.updateConsumer(existing.getUuid(), updated);

        verify(entCertService, atMost(1)).regenerateCertificatesOf(existing, true);
        verify(sink).queueEvent(any());
    }

    @Test
    public void throwsAnExceptionWhenEnvironmentNotFound() {
        String uuid = "A Consumer";
        EnvironmentDTO changedEnvironment = new EnvironmentDTO()
            .id("42")
            .name("environment");

        ConsumerDTO updated = new ConsumerDTO();
        updated.setUuid(uuid);
        updated.addEnvironmentsItem(changedEnvironment);

        Consumer existing = getFakeConsumer();
        existing.setUuid(updated.getUuid());

        when(consumerCurator.verifyAndLookupConsumer(existing.getUuid())).thenReturn(existing);
        when(environmentCurator.get(changedEnvironment.getId())).thenReturn(null);

        assertThrows(NotFoundException.class,
            () -> resource.updateConsumer(existing.getUuid(), updated));
    }

    @Test
    public void canUpdateName() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName("newname");

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        resource.updateConsumer(consumer.getUuid(), updated);
        verify(consumerCurator, times(1)).update(consumerCaptor.capture());

        Consumer mergedConsumer = consumerCaptor.getValue();
        assertEquals(updated.getName(), mergedConsumer.getName());
    }

    @Test
    public void updatedNameRegeneratesIdCert() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName("newname");

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        resource.updateConsumer(consumer.getUuid(), updated);
        verify(consumerCurator, times(1)).update(consumerCaptor.capture());
        Consumer mergedConsumer = consumerCaptor.getValue();

        assertEquals(updated.getName(), mergedConsumer.getName());
        assertNotNull(mergedConsumer.getIdCert());
    }

    @Test
    public void sameNameDoesntRegenIdCert() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setName("oldname");
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName("oldname");

        resource.updateConsumer(consumer.getUuid(), updated);

        assertEquals(updated.getName(), consumer.getName());
        assertNull(consumer.getIdCert());
    }

    @Test
    public void updatingToNullNameIgnoresName() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setName("old name");
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName(null);

        resource.updateConsumer(consumer.getUuid(), updated);
        assertEquals("old name", consumer.getName());
    }

    @Test
    public void updatingToInvalidCharacterNameNotAllowed() {
        ConsumerDTO consumer = getFakeConsumerDTO();
        consumer.setName("old name");
        ConsumerDTO updated = new ConsumerDTO();
        updated.setName("#a name");

        assertThrows(BadRequestException.class,
            () -> resource.updateConsumer(consumer.getUuid(), updated));
    }

    @Test
    public void consumerCapabilityUpdate() {
        ConsumerType ct = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        this.mockConsumerType(ct);

        Consumer c = getFakeConsumer();
        Set<ConsumerCapability> caps = new HashSet<>();
        ConsumerCapability cca = new ConsumerCapability("capability_a");
        ConsumerCapability ccb = new ConsumerCapability("capability_b");
        ConsumerCapability ccc = new ConsumerCapability("capability_c");
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
        resource.updateConsumer(c.getUuid(), updated);
        assertEquals(3, c.getCapabilities().size());

        // empty capability list in update object does change existing
        updated = new ConsumerDTO();
        updated.setCapabilities(new HashSet<>());
        resource.updateConsumer(c.getUuid(), updated);
        assertEquals(0, c.getCapabilities().size());
    }

    @Test
    public void consumerChangeDetection() {
        ConsumerCapability cca = new ConsumerCapability("capability_a");
        ConsumerCapability ccb = new ConsumerCapability("capability_b");
        ConsumerCapability ccc = new ConsumerCapability("capability_c");
        CapabilityDTO ccaDto = new CapabilityDTO().name(cca.getName());
        CapabilityDTO ccbDto = new CapabilityDTO().name(ccb.getName());
        CapabilityDTO cccDto = new CapabilityDTO().name(ccc.getName());

        Consumer existing = this.getFakeConsumer()
            .setCapabilities(Set.of(cca, ccb, ccc));

        ConsumerDTO update = new ConsumerDTO()
            .id(existing.getId())
            .uuid(existing.getUuid());

        update.setCapabilities(Set.of(ccaDto, ccbDto, cccDto));
        assertFalse(resource.performConsumerUpdates(update, existing, testMigration));

        update.setCapabilities(Set.of(ccbDto));
        assertTrue(resource.performConsumerUpdates(update, existing, testMigration));

        update.setCapabilities(null);
        assertFalse(resource.performConsumerUpdates(update, existing, testMigration));

        verifyNoInteractions(this.complianceRules);
        verifyNoInteractions(this.systemPurposeComplianceRules);
    }

    @Test
    public void shouldDetectComplianceChange() {
        Consumer consumer = getFakeConsumer();
        ConsumerDTO updated = this.translator.translate(consumer, ConsumerDTO.class);
        updated.setFacts(Map.ofEntries(
            Map.entry(ComplianceFacts.CPU_SOCKETS.getFactKey(), "changed")));

        assertTrue(resource.performConsumerUpdates(updated, consumer, testMigration));
        verify(this.complianceRules)
            .getStatus(any(Consumer.class), nullable(Date.class), anyBoolean(), anyBoolean());
    }

    @Test
    public void shouldDetectSysPurposeChange() {
        Consumer consumer = getFakeConsumer();
        ConsumerDTO updated = this.translator.translate(consumer, ConsumerDTO.class);
        updated.usage("Changed");

        assertTrue(resource.performConsumerUpdates(updated, consumer, testMigration));
        verify(this.systemPurposeComplianceRules)
            .getStatus(any(Consumer.class), anyCollection(), isNull(), anyBoolean());
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
        updated.setLastCheckin(Util.toDateTime(then));
        resource.updateConsumer(c.getUuid(), updated);
    }

    private Consumer createConsumerWithGuests(Owner owner, String... guestIds) {
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

    private ConsumerDTO createConsumerDTOWithGuests(String... guestIds) {
        Consumer consumer = createConsumerWithGuests(createOwner(), guestIds);
        // re-add guestIds as consumer translator removes them.
        List<GuestIdDTO> guestIdDTOS = new LinkedList<>();
        for (GuestId guestId : consumer.getGuestIds()) {
            guestIdDTOS.add(translator.translate(guestId, GuestIdDTO.class));
        }
        return translator.translate(consumer, ConsumerDTO.class).guestIds(guestIdDTOS);
    }

    @Test
    public void canUpdateConsumerEnvironmentWithPriorities() {
        Environment changedEnvironment = new Environment("42", "environment", null);

        Consumer existing = getFakeConsumer();

        ConsumerDTO updated = new ConsumerDTO();
        Owner owner = createOwner();
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setId(owner.getId());

        updated.addEnvironmentsItem(translator.translate(changedEnvironment, EnvironmentDTO.class));
        updated.setOwner(new NestedOwnerDTO());

        when(environmentCurator.get(changedEnvironment.getId())).thenReturn(changedEnvironment);
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);
        when(environmentCurator.exists(changedEnvironment.getId())).thenReturn(true);

        resource.updateConsumer(existing.getUuid(), updated);
        assertEquals(1, existing.getEnvironmentIds().size());
    }

    @Test
    public void canUpdateConsumerNotImpactedWithNullEnvironments() {
        Environment env1 = new Environment("1", "environment-1", null);
        Environment env2 = new Environment("2", "environment-2", null);

        Consumer existing = getFakeConsumer();
        existing.addEnvironment(env1);
        existing.addEnvironment(env2);

        ConsumerDTO updated = new ConsumerDTO();
        Owner owner = createOwner();
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setId(owner.getId());

        updated.setEnvironments(null);
        updated.setOwner(new NestedOwnerDTO());

        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);

        resource.updateConsumer(existing.getUuid(), updated);
        assertEquals(2, existing.getEnvironmentIds().size());
    }

    @Test
    public void testUpdateConsumerCanRemoveAllEnvironmentsWithEmptyList() {
        Owner owner = this.mockOwner("owner-1");
        Environment env1 = this.mockEnvironment(owner, "1", "environment-1");
        Environment env2 = this.mockEnvironment(owner, "2", "environment-2");

        Consumer consumer = this.mockConsumer(owner)
            .addEnvironment(env1)
            .addEnvironment(env2);

        ConsumerDTO update = new ConsumerDTO()
            .uuid(consumer.getUuid())
            .environments(List.of());

        resource.updateConsumer(consumer.getUuid(), update);

        assertThat(consumer.getEnvironmentIds())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void canUpdateConsumerEnvironmentWithUpdatedPriorities() {
        Owner owner = this.mockOwner("owner-1");
        Environment env1 = this.mockEnvironment(owner, "1", "environment-1");
        Environment env2 = this.mockEnvironment(owner, "2", "environment-2");

        Consumer consumer = this.mockConsumer(owner)
            .addEnvironment(env1)
            .addEnvironment(env2);

        // note: order matters here
        List<EnvironmentDTO> updatedEnvironments = List.of(
            this.translator.translate(env2, EnvironmentDTO.class),
            this.translator.translate(env1, EnvironmentDTO.class));

        ConsumerDTO update = new ConsumerDTO()
            .uuid(consumer.getUuid())
            .environments(updatedEnvironments);

        resource.updateConsumer(consumer.getUuid(), update);

        assertThat(consumer.getEnvironmentIds())
            .isNotNull()
            .containsExactly(env2.getId(), env1.getId());
    }

    @Test
    public void canUpdateConsumerEnvironmentWithNewEnvironments() {
        Environment env1 = new Environment("1", "environment-1", null);
        Environment env2 = new Environment("2", "environment-2", null);

        Consumer existing = getFakeConsumer();
        existing.addEnvironment(env1);
        existing.addEnvironment(env2);

        ConsumerDTO updated = new ConsumerDTO();
        Owner owner = createOwner();
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setId(owner.getId());

        // Brand-new environments to be updated to existing consumer.
        Environment env3 = new Environment("3", "environment-3", null);
        Environment env4 = new Environment("4", "environment-4", null);

        List<EnvironmentDTO> updatedEnvs = new ArrayList<>();
        updatedEnvs.add(translator.translate(env4, EnvironmentDTO.class));
        updatedEnvs.add(translator.translate(env3, EnvironmentDTO.class));
        updated.setEnvironments(updatedEnvs);
        updated.setOwner(new NestedOwnerDTO());

        when(environmentCurator.get(env3.getId())).thenReturn(env3);
        when(environmentCurator.get(env4.getId())).thenReturn(env4);
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);
        when(environmentCurator.exists(env3.getId())).thenReturn(true);
        when(environmentCurator.exists(env4.getId())).thenReturn(true);

        resource.updateConsumer(existing.getUuid(), updated);
        assertEquals(existing.getEnvironmentIds().get(0), env4.getId());
        assertEquals(existing.getEnvironmentIds().get(1), env3.getId());
    }

    @Test
    public void throwsExceptionWhenSameEnvironmentIsAddedToConsumer() {
        Environment env1 = new Environment("1", "environment-1", null);
        Environment env2 = new Environment("2", "environment-2", null);

        Consumer existing = getFakeConsumer();
        existing.addEnvironment(env1);
        existing.addEnvironment(env2);

        ConsumerDTO updated = new ConsumerDTO();
        Owner owner = createOwner();
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setId(owner.getId());

        List<EnvironmentDTO> updatedEnvs = new ArrayList<>();
        updatedEnvs.add(translator.translate(env1, EnvironmentDTO.class));
        updatedEnvs.add(translator.translate(env2, EnvironmentDTO.class));
        updatedEnvs.add(translator.translate(env1, EnvironmentDTO.class)); // Repeated Environment
        updated.setEnvironments(updatedEnvs);
        updated.setOwner(new NestedOwnerDTO());

        when(environmentCurator.get(env1.getId())).thenReturn(env1);
        when(environmentCurator.get(env2.getId())).thenReturn(env2);
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);
        when(environmentCurator.exists(env1.getId())).thenReturn(true);
        when(environmentCurator.exists(env2.getId())).thenReturn(true);

        assertThrows(ConflictException.class,
            () -> resource.updateConsumer(existing.getUuid(), updated));
    }

    @Test
    public void throwsExceptionWhenNullEntryAddedToMultipleEnvList() {
        Environment env1 = new Environment("1", "environment-1", null);
        Environment env2 = new Environment("2", "environment-2", null);

        Consumer existing = getFakeConsumer();
        existing.addEnvironment(env1);
        existing.addEnvironment(env2);

        ConsumerDTO updated = new ConsumerDTO();
        Owner owner = createOwner();
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setId(owner.getId());

        List<EnvironmentDTO> updatedEnvs = new ArrayList<>();
        updatedEnvs.add(translator.translate(env1, EnvironmentDTO.class));
        updatedEnvs.add(null); // null entry added
        updatedEnvs.add(translator.translate(env2, EnvironmentDTO.class));
        updated.setEnvironments(updatedEnvs);
        updated.setOwner(new NestedOwnerDTO());

        when(environmentCurator.get(env1.getId())).thenReturn(env1);
        when(environmentCurator.get(env2.getId())).thenReturn(env2);
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);
        when(environmentCurator.exists(env1.getId())).thenReturn(true);
        when(environmentCurator.exists(env2.getId())).thenReturn(true);

        assertThrows(NotFoundException.class,
            () -> resource.updateConsumer(existing.getUuid(), updated));
    }

    @Test
    public void shouldPreserveEnvPriorityOnConsumerWhenAnEnvironmentIsRemoved() {
        Environment env1 = new Environment("1", "environment-1", null);
        Environment env2 = new Environment("2", "environment-2", null);
        Environment env3 = new Environment("3", "environment-3", null);
        Environment env4 = new Environment("4", "environment-4", null);
        Environment env11 = new Environment("11", "environment-11", null);
        Environment env111 = new Environment("111", "environment-111", null);

        Consumer existing = getFakeConsumer();
        existing.addEnvironment(env1);
        existing.addEnvironment(env2);
        existing.addEnvironment(env3);
        existing.addEnvironment(env4);
        existing.addEnvironment(env11);
        existing.addEnvironment(env111);

        ConsumerDTO updated = new ConsumerDTO();
        Owner owner = createOwner();
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setId(owner.getId());

        List<EnvironmentDTO> updatedEnvs = new ArrayList<>();
        updatedEnvs.add(translator.translate(env1, EnvironmentDTO.class));
        updatedEnvs.add(translator.translate(env3, EnvironmentDTO.class));
        updatedEnvs.add(translator.translate(env4, EnvironmentDTO.class));
        updatedEnvs.add(translator.translate(env11, EnvironmentDTO.class));
        updatedEnvs.add(translator.translate(env111, EnvironmentDTO.class));

        updated.setEnvironments(updatedEnvs);
        updated.setOwner(new NestedOwnerDTO());

        when(environmentCurator.get(env1.getId())).thenReturn(env1);
        when(environmentCurator.get(env3.getId())).thenReturn(env3);
        when(environmentCurator.get(env4.getId())).thenReturn(env4);
        when(environmentCurator.get(env11.getId())).thenReturn(env11);
        when(environmentCurator.get(env111.getId())).thenReturn(env111);

        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);
        when(environmentCurator.exists(env1.getId())).thenReturn(true);
        when(environmentCurator.exists(env3.getId())).thenReturn(true);
        when(environmentCurator.exists(env4.getId())).thenReturn(true);
        when(environmentCurator.exists(env11.getId())).thenReturn(true);
        when(environmentCurator.exists(env111.getId())).thenReturn(true);

        resource.updateConsumer(existing.getUuid(), updated);
        assertEquals(existing.getEnvironmentIds().get(0), env1.getId());
        assertEquals(existing.getEnvironmentIds().get(1), env3.getId());
        assertEquals(existing.getEnvironmentIds().get(2), env4.getId());
        assertEquals(existing.getEnvironmentIds().get(3), env11.getId());
        assertEquals(existing.getEnvironmentIds().get(4), env111.getId());
    }

    @Test
    void testUpdateConsumerExistingCloudDataUpdateFromCalled() {
        String uuid = "uuid-123";
        ConsumerDTO dto = new ConsumerDTO();
        dto.setFacts(Map.of("aws_account_id", "123"));
        ConsumerCloudData existingCloudData = mock(ConsumerCloudData.class);
        ConsumerCloudData changedCloudData = mock(ConsumerCloudData.class);
        Consumer toUpdate = spy(new Consumer());

        when(toUpdate.checkForCloudIdentifierFacts(dto.getFacts())).thenReturn(true);
        when(toUpdate.getConsumerCloudData()).thenReturn(existingCloudData);
        when(consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(toUpdate);
        when(cloudDataBuilder.build(dto)).thenReturn(Optional.of(changedCloudData));

        resource.updateConsumer(uuid, dto);

        verify(existingCloudData).updateFrom(changedCloudData);
        verify(toUpdate, never()).setConsumerCloudData(any());
    }

    @Test
    void testUpdateConsumerNoExistingCloudDataSetConsumerCloudDataCalled() {
        String uuid = "uuid-456";
        ConsumerDTO dto = new ConsumerDTO();
        dto.setFacts(Map.of("aws_account_id", "123"));
        Consumer toUpdate = spy(new Consumer());
        ConsumerCloudData newCloudData = mock(ConsumerCloudData.class);

        when(toUpdate.checkForCloudIdentifierFacts(dto.getFacts())).thenReturn(true);
        when(toUpdate.getConsumerCloudData()).thenReturn(null);
        when(consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(toUpdate);
        when(cloudDataBuilder.build(dto)).thenReturn(Optional.of(newCloudData));

        resource.updateConsumer(uuid, dto);

        verify(toUpdate).setConsumerCloudData(newCloudData);
    }

    @Test
    void testUpdateConsumerEmptyChangedCloudData() {
        String uuid = "uuid-789";
        ConsumerDTO dto = new ConsumerDTO();
        dto.setFacts(Map.of("aws_account_id", "123"));
        Consumer toUpdate = spy(new Consumer());
        ConsumerCloudData existingCloudData = mock(ConsumerCloudData.class);

        when(toUpdate.checkForCloudIdentifierFacts(dto.getFacts())).thenReturn(true);
        when(toUpdate.getConsumerCloudData()).thenReturn(existingCloudData);
        when(consumerCurator.verifyAndLookupConsumer(uuid)).thenReturn(toUpdate);
        when(cloudDataBuilder.build(dto)).thenReturn(Optional.empty());

        resource.updateConsumer(uuid, dto);

        verify(existingCloudData, never()).updateFrom(any());
        verify(toUpdate, never()).setConsumerCloudData(any());
    }
}
