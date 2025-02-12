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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.EntitlementCertificateService;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.Owner;
import org.candlepin.pki.certs.AnonymousCertificateGenerator;
import org.candlepin.pki.certs.IdentityCertificateGenerator;
import org.candlepin.pki.certs.SCACertificateGenerator;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerCloudDataBuilder;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.FactValidator;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Provider;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsumerContentOverrideResourceTest extends DatabaseTestFixture {

    @Mock
    private EntitlementCertServiceAdapter entitlementCertServiceAdapter;
    @Mock
    private SubscriptionServiceAdapter subscriptionServiceAdapter;
    @Mock
    private PoolManager poolManager;
    @Mock
    private RefresherFactory refresherFactory;
    @Mock
    private ComplianceRules complianceRules;
    @Mock
    private SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock
    private EventFactory eventFactory;
    @Mock
    private ConsumerRules consumerRules;
    @Mock
    private CalculatedAttributesUtil calculatedAttributesUtil;
    @Mock
    private ConsumerBindUtil consumerBindUtil;
    @Mock
    private ConsumerEnricher consumerEnricher;
    @Mock
    private ContentAccessManager contentAccessManager;
    @Mock
    private EventSink sink;
    @Mock
    private IdentityCertificateGenerator idCertGenerator;
    @Mock
    private Entitler entitler;
    @Mock
    private ManifestManager manifestManager;
    @Mock
    private UserServiceAdapter userServiceAdapter;
    @Mock
    private JobManager jobManager;
    @Mock
    private DTOValidator dtoValidator;
    @Mock
    private FactValidator factValidator;
    @Mock
    private Provider<GuestMigration> guestMigrationProvider;
    @Mock
    private PoolService poolService;
    @Mock
    private PrincipalProvider principalProvider;
    @Mock
    private EntitlementCertificateService entCertService;
    @Mock
    private OwnerServiceAdapter ownerService;
    @Mock
    private SCACertificateGenerator scaCertificateGenerator;
    @Mock
    private AnonymousCertificateGenerator anonymousCertificateGenerator;
    @Mock
    private ConsumerCloudDataBuilder consumerCloudDataBuilder;

    private ConsumerResource resource;

    @BeforeEach
    public void setUp() {
        Principal principal = mock(Principal.class);
        when(principal.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(true);
        ResteasyContext.pushContext(Principal.class, principal);

        this.resource = new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionServiceAdapter,
            this.entitlementCurator,
            this.idCertGenerator,
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
            this.factValidator,
            new ConsumerTypeValidator(consumerTypeCurator, i18n),
            this.consumerEnricher,
            this.guestMigrationProvider,
            this.modelTranslator,
            this.jobManager,
            this.dtoValidator,
            this.principalProvider,
            new ContentOverrideValidator(this.config, this.i18n),
            this.consumerContentOverrideCurator,
            this.entCertService,
            this.poolService,
            this.environmentContentCurator,
            this.anonymousCloudConsumerCurator,
            this.anonymousContentAccessCertCurator,
            this.ownerService,
            this.scaCertificateGenerator,
            this.anonymousCertificateGenerator,
            this.consumerCloudDataBuilder
        );
    }

    private Consumer createConsumer() {
        Owner owner = this.createOwner();
        return this.createConsumer(owner);
    }

    private List<ConsumerContentOverride> createOverrides(Consumer consumer, int offset, int count) {
        List<ConsumerContentOverride> overrides = new LinkedList<>();

        for (int i = offset; i < offset + count; ++i) {
            ConsumerContentOverride cco = new ConsumerContentOverride()
                .setConsumer(consumer)
                .setContentLabel("content_label-" + i)
                .setName("override_name-" + i)
                .setValue("override_value-" + i);

            overrides.add(this.consumerContentOverrideCurator.create(cco));
        }

        return overrides;
    }

    @Test
    public void testGetOverrides() {
        Consumer consumer = this.createConsumer();
        List<ConsumerContentOverride> overrides = this.createOverrides(consumer, 1, 3);

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class))
            .toList();

        List<ContentOverrideDTO> actual = this.resource
            .listConsumerContentOverrides(consumer.getUuid())
            .toList();

        assertThat(actual)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testGetOverridesEmptyList() {
        Consumer consumer = this.createConsumer();

        List<ContentOverrideDTO> actual = this.resource
            .listConsumerContentOverrides(consumer.getUuid())
            .toList();

        assertThat(actual)
            .isEmpty();
    }

    @Test
    public void testDeleteOverrideUsingName() {
        Consumer consumer = this.createConsumer();
        List<ConsumerContentOverride> overrides = this.createOverrides(consumer, 1, 3);

        ConsumerContentOverride toDelete = overrides.remove(1);
        ContentOverrideDTO toDeleteDTO = new ContentOverrideDTO()
            .contentLabel(toDelete.getContentLabel())
            .name(toDelete.getName());

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class))
            .toList();

        List<ContentOverrideDTO> actual = this.resource
            .deleteConsumerContentOverrides(consumer.getUuid(), List.of(toDeleteDTO))
            .toList();

        assertThat(actual)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testDeleteOverridesUsingContentLabel() {
        Consumer consumer = this.createConsumer();
        List<ConsumerContentOverride> overrides = this.createOverrides(consumer, 1, 3);

        ConsumerContentOverride toDelete = overrides.remove(1);
        ContentOverrideDTO toDeleteDTO = new ContentOverrideDTO()
            .contentLabel(toDelete.getContentLabel());

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class))
            .toList();

        List<ContentOverrideDTO> actual = this.resource
            .deleteConsumerContentOverrides(consumer.getUuid(), List.of(toDeleteDTO))
            .toList();

        assertThat(actual)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyList() {
        Consumer consumer = this.createConsumer();
        this.createOverrides(consumer, 1, 3);

        List<ContentOverrideDTO> actual = this.resource
            .deleteConsumerContentOverrides(consumer.getUuid(), List.of())
            .toList();

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyContentLabel() {
        Consumer consumer = this.createConsumer();
        this.createOverrides(consumer, 1, 3);

        List<ContentOverrideDTO> actual = this.resource
            .deleteConsumerContentOverrides(consumer.getUuid(), List.of())
            .toList();

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testAddOverride() {
        Consumer consumer = this.createConsumer();

        ContentOverrideDTO dto1 = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        List<ContentOverrideDTO> output1 = this.resource
            .addConsumerContentOverrides(consumer.getUuid(), List.of(dto1))
            .toList();

        assertThat(output1)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrder(dto1);

        // Add a second to ensure we don't clobber the first
        ContentOverrideDTO dto2 = new ContentOverrideDTO()
            .contentLabel("test_label-2")
            .name("override_name-2")
            .value("override_value-2");

        List<ContentOverrideDTO> output2 = this.resource
            .addConsumerContentOverrides(consumer.getUuid(), List.of(dto2))
            .toList();

        assertThat(output2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrder(dto1, dto2);
    }

    @Test
    public void testAddOverrideOverwritesExistingWhenMatched() {
        Consumer consumer = this.createConsumer();

        ContentOverrideDTO dto1 = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        List<ContentOverrideDTO> output1 = this.resource
            .addConsumerContentOverrides(consumer.getUuid(), List.of(dto1))
            .toList();

        assertThat(output1)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrder(dto1);

        // Add a "new" override that has the same label and name as the first which should inherit
        // the new value
        ContentOverrideDTO dto2 = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value-2");

        List<ContentOverrideDTO> output2 = this.resource
            .addConsumerContentOverrides(consumer.getUuid(), List.of(dto2))
            .toList();

        assertThat(output2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrder(dto2);
    }

    @Test
    public void testAddOverrideFailsValidationWithNoParent() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> this.resource.addConsumerContentOverrides(null, List.of(dto)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testAddOverrideFailsValidationWithNullOrEmptyLabel(String label) {
        Consumer consumer = this.createConsumer();

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(label)
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> this.resource.addConsumerContentOverrides(consumer.getUuid(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithLongLabel() {
        Consumer consumer = this.createConsumer();

        String longString = "a".repeat(ContentOverride.MAX_NAME_AND_LABEL_LENGTH + 1);

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(longString)
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> this.resource.addConsumerContentOverrides(consumer.getUuid(), List.of(dto)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testAddOverrideFailsValidationWithNullOrEmptyName(String name) {
        Consumer consumer = this.createConsumer();

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(name)
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> this.resource.addConsumerContentOverrides(consumer.getUuid(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithLongName() {
        Consumer consumer = this.createConsumer();

        String longString = "a".repeat(ContentOverride.MAX_NAME_AND_LABEL_LENGTH + 1);

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(longString)
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> this.resource.addConsumerContentOverrides(consumer.getUuid(), List.of(dto)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testAddOverrideFailsValidationWithNullOrEmptyValue(String value) {
        Consumer consumer = this.createConsumer();

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(value);

        assertThrows(BadRequestException.class,
            () -> this.resource.addConsumerContentOverrides(consumer.getUuid(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithLongValue() {
        Consumer consumer = this.createConsumer();

        String longString = "a".repeat(ContentOverride.MAX_VALUE_LENGTH + 1);

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(longString);

        assertThrows(BadRequestException.class,
            () -> this.resource.addConsumerContentOverrides(consumer.getUuid(), List.of(dto)));
    }
}
