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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestIdCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.FactValidator;
import org.candlepin.util.ServiceLevelValidator;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.inject.Provider;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsumerContentOverrideResourceTest extends DatabaseTestFixture {

    @Mock private ConsumerCurator consumerCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private EntitlementCertServiceAdapter entitlementCertServiceAdapter;
    @Mock private SubscriptionServiceAdapter subscriptionServiceAdapter;
    @Mock private ProductServiceAdapter mockProductServiceAdapter;
    @Mock private PoolManager poolManager;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private ComplianceRules complianceRules;
    @Mock private SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock private ServiceLevelValidator serviceLevelValidator;
    @Mock private ActivationKeyRules activationKeyRules;
    @Mock private EventFactory eventFactory;
    @Mock private EventBuilder eventBuilder;
    @Mock private ConsumerRules consumerRules;
    @Mock private CalculatedAttributesUtil calculatedAttributesUtil;
    @Mock private ConsumerBindUtil consumerBindUtil;
    @Mock private ConsumerEnricher consumerEnricher;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private ContentAccessManager contentAccessManager;
    @Mock private EventSink sink;
    @Mock private EnvironmentCurator environmentCurator;
    @Mock private DistributorVersionCurator distributorVersionCurator;
    @Mock private IdentityCertServiceAdapter identityCertServiceAdapter;
    @Mock private ActivationKeyCurator activationKeyCurator;
    @Mock private Entitler entitler;
    @Mock private ManifestManager manifestManager;
    @Mock private UserServiceAdapter userServiceAdapter;
    @Mock private DeletedConsumerCurator deletedConsumerCurator;
    @Mock private JobManager jobManager;
    @Mock private DTOValidator dtoValidator;
    @Mock private FactValidator factValidator;
    @Mock private Provider<GuestMigration> guestMigrationProvider;
    @Mock private GuestIdCurator guestIdCurator;
    @Mock private PrincipalProvider principalProvider;

    private Consumer consumer;
    private ContentOverrideValidator contentOverrideValidator;
    private ConsumerResource resource;

    @BeforeEach
    public void setUp() {
        this.consumer = this.createConsumer(this.createOwner());

        Principal principal = mock(Principal.class);
        when(principal.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(true);
        ResteasyContext.pushContext(Principal.class, principal);

        this.contentOverrideValidator = new ContentOverrideValidator(this.config, this.i18n);
        when(this.consumerCurator.verifyAndLookupConsumer(anyString()))
            .thenReturn(this.consumer);

        this.resource = new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionServiceAdapter,
            this.mockProductServiceAdapter,
            this.entitlementCurator,
            this.identityCertServiceAdapter,
            this.entitlementCertServiceAdapter,
            this.i18n,
            this.sink,
            this.eventFactory,
            this.userServiceAdapter,
            this.poolManager,
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
            this.guestIdCurator,
            this.principalProvider,
            this.contentOverrideValidator,
            this.consumerContentOverrideCurator
        );
    }

    private List<ConsumerContentOverride> createOverrides(Consumer consumer, int offset, int count) {

        List<ConsumerContentOverride> overrides = new LinkedList<>();

        for (int i = offset; i < offset + count; ++i) {
            ConsumerContentOverride cco = new ConsumerContentOverride();
            cco.setConsumer(consumer);
            cco.setContentLabel("content_label-" + i);
            cco.setName("override_name-" + i);
            cco.setValue("override_value-" + i);

            overrides.add(this.consumerContentOverrideCurator.create(cco));
        }

        return overrides;
    }

    /**
     * Removes the created and updated timestamps from the DTOs to make comparison easier
     */
    private List<ContentOverrideDTO> stripTimestamps(List<ContentOverrideDTO> list) {
        if (list != null) {
            for (ContentOverrideDTO dto : list) {
                dto.setCreated(null);
                dto.setUpdated(null);
            }
        }

        return list;
    }

    private List<ContentOverrideDTO> stripTimestamps(Iterable<ContentOverrideDTO> list) {
        return stripTimestamps(StreamSupport.stream(list.spliterator(), false).collect(Collectors.toList()));
    }

    private long sizeOf(Iterable<ContentOverrideDTO> list) {
        return StreamSupport.stream(list.spliterator(), false).count();
    }

    /**
     * Compares the collections of override DTOs by converting them to generic override lists and
     * stripping their timestamps.
     */
    private void compareOverrideDTOs(List<ContentOverrideDTO> expected, Iterable<ContentOverrideDTO> actual) {
        assertEquals(this.stripTimestamps(expected), this.stripTimestamps(actual));
    }

    private String getLongString() {
        StringBuilder builder = new StringBuilder();

        while (builder.length() < ContentOverrideValidator.MAX_VALUE_LENGTH) {
            builder.append("longstring");
        }

        return builder.toString();
    }

    @Test
    public void testGetOverrides() {
        List<ConsumerContentOverride> overrides = this.createOverrides(this.consumer, 1, 3);

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(
            ConsumerContentOverride.class, ContentOverrideDTO.class))
            .collect(Collectors.toList());

        Iterable<ContentOverrideDTO> actual = this.resource
            .listConsumerContentOverrides(this.consumer.getUuid());

        this.compareOverrideDTOs(expected, actual);
    }

    @Test
    public void testGetOverridesEmptyList() {
        Iterable<ContentOverrideDTO> actual = this.resource
            .listConsumerContentOverrides(this.consumer.getUuid());

        assertEquals(0, sizeOf(actual));
    }

    @Test
    public void testDeleteOverrideUsingName() {
        List<ConsumerContentOverride> overrides = this.createOverrides(this.consumer, 1, 3);

        ConsumerContentOverride toDelete = overrides.remove(1);
        ContentOverrideDTO toDeleteDTO = new ContentOverrideDTO()
            .contentLabel(toDelete.getContentLabel())
            .name(toDelete.getName());

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(
            ConsumerContentOverride.class, ContentOverrideDTO.class))
            .collect(Collectors.toList());

        Iterable<ContentOverrideDTO> actual = this.resource
            .deleteConsumerContentOverrides(this.consumer.getUuid(), Arrays.asList(toDeleteDTO));

        this.compareOverrideDTOs(expected, actual);
    }

    @Test
    public void testDeleteOverridesUsingContentLabel() {
        List<ConsumerContentOverride> overrides = this.createOverrides(this.consumer, 1, 3);

        ConsumerContentOverride toDelete = overrides.remove(1);
        ContentOverrideDTO toDeleteDTO = new ContentOverrideDTO()
            .contentLabel(toDelete.getContentLabel());

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(
            ConsumerContentOverride.class, ContentOverrideDTO.class))
            .collect(Collectors.toList());

        Iterable<ContentOverrideDTO> actual = this.resource
            .deleteConsumerContentOverrides(this.consumer.getUuid(), Arrays.asList(toDeleteDTO));

        this.compareOverrideDTOs(expected, actual);
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyList() {
        List<ConsumerContentOverride> overrides = this.createOverrides(this.consumer, 1, 3);

        Iterable<ContentOverrideDTO> actual = this.resource
            .deleteConsumerContentOverrides(this.consumer.getUuid(), Collections.emptyList());

        assertEquals(0, sizeOf(actual));
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyContentLabel() {
        List<ConsumerContentOverride> overrides = this.createOverrides(this.consumer, 1, 3);

        Iterable<ContentOverrideDTO> actual = this.resource
            .deleteConsumerContentOverrides(this.consumer.getUuid(), Collections.emptyList());

        assertEquals(0, sizeOf(actual));
    }

    @Test
    public void testAddOverride() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        Iterable<ContentOverrideDTO> actual = this.resource
            .addConsumerContentOverrides(this.consumer.getUuid(), overrides);

        this.compareOverrideDTOs(overrides, actual);

        // Add a second to ensure we don't clobber the first
        dto = new ContentOverrideDTO()
            .contentLabel("test_label-2")
            .name("override_name-2")
            .value("override_value-2");

        overrides.add(dto);

        actual = this.resource.addConsumerContentOverrides(this.consumer.getUuid(), Arrays.asList(dto));

        this.compareOverrideDTOs(overrides, actual);
    }

    @Test
    public void testAddOverrideOverwritesExistingWhenMatched() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        Iterable<ContentOverrideDTO> actual = this.resource
            .addConsumerContentOverrides(this.consumer.getUuid(), overrides);

        this.compareOverrideDTOs(overrides, actual);

        // Add a "new" override that has the same label and name as the first which should inherit
        // the new value
        dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value-2");

        overrides.clear();
        overrides.add(dto);

        actual = this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides);

        this.compareOverrideDTOs(overrides, actual);
    }

    @Test
    public void testAddOverrideFailsValidationWithNoParent() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        Iterable<ContentOverrideDTO> actual = this.resource
            .addConsumerContentOverrides(this.consumer.getUuid(), overrides);

        this.compareOverrideDTOs(overrides, actual);
    }

    @Test
    public void testAddOverrideFailsValidationWithNullLabel() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(null)
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides)
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithEmptyLabel() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("")
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides)
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithLongLabel() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(this.getLongString())
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides)
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithNullName() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(null)
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides)
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithEmptyName() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("")
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides)
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithLongName() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(this.getLongString())
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides)
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithNullValue() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(null);

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides)
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithEmptyValue() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value("");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides)
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithLongValue() {
        ConsumerDTO kdto = this.modelTranslator.translate(this.consumer, ConsumerDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(this.getLongString());

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            this.resource.addConsumerContentOverrides(this.consumer.getUuid(), overrides)
        );
    }
}
