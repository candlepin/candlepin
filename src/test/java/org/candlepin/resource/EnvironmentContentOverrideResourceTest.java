/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import org.candlepin.async.JobManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.EntitlementCertificateService;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContentOverride;
import org.candlepin.model.Owner;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.RdbmsExceptionTranslator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EnvironmentContentOverrideResourceTest extends DatabaseTestFixture {

    // TODO: Stop mocking these if we actually start hitting operations that utilize them in this
    // suite.
    @Mock
    private ConsumerResource mockConsumerResource;
    @Mock
    private PoolService mockPoolService;
    @Mock
    private RdbmsExceptionTranslator mockRdbmsExceptionTranslator;
    @Mock
    private JobManager mockJobManager;
    @Mock
    private ContentAccessManager mockContentAccessManager;
    @Mock
    private EntitlementCertificateService mockEntitlementCertService;

    private DTOValidator dtoValidator;
    private ContentOverrideValidator contentOverrideValidator;


    @BeforeEach
    public void init() throws Exception {
        super.init();

        this.dtoValidator = new DTOValidator(this.i18n);
        this.contentOverrideValidator = new ContentOverrideValidator(this.config, this.i18n);
    }

    private EnvironmentResource buildResource() {
        return new EnvironmentResource(
            this.environmentCurator,
            this.i18n,
            this.environmentContentCurator,
            this.mockConsumerResource,
            this.mockPoolService,
            this.consumerCurator,
            this.contentCurator,
            this.mockRdbmsExceptionTranslator,
            this.modelTranslator,
            this.mockJobManager,
            this.dtoValidator,
            this.contentOverrideValidator,
            this.mockContentAccessManager,
            this.certSerialCurator,
            this.identityCertificateCurator,
            this.caCertCurator,
            this.entitlementCurator,
            this.mockEntitlementCertService,
            this.environmentContentOverrideCurator);
    }

    private Environment createEnvironment() {
        Owner owner = this.createOwner();
        return this.createEnvironment(owner);
    }

    private List<EnvironmentContentOverride> createOverrides(Environment parent, int offset, int count) {
        List<EnvironmentContentOverride> overrides = new ArrayList<>();

        for (int i = offset; i < offset + count; ++i) {
            EnvironmentContentOverride override = new EnvironmentContentOverride()
                .setEnvironment(parent)
                .setContentLabel("content_label-" + i)
                .setName("override_name-" + i)
                .setValue("override_value-" + i);

            overrides.add(this.environmentContentOverrideCurator.create(override));
        }

        return overrides;
    }

    @Test
    public void testGetOverrides() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();
        List<EnvironmentContentOverride> overrides = this.createOverrides(environment, 1, 3);

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class))
            .toList();

        List<ContentOverrideDTO> actual = resource.getEnvironmentContentOverrides(environment.getId())
            .toList();

        assertThat(actual)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testGetOverridesEmptyList() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();

        List<ContentOverrideDTO> actual = resource.getEnvironmentContentOverrides(environment.getId())
            .toList();

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testDeleteOverrideUsingName() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();
        List<EnvironmentContentOverride> overrides = this.createOverrides(environment, 1, 3);

        EnvironmentContentOverride toDelete = overrides.remove(1);
        ContentOverrideDTO toDeleteDTO = new ContentOverrideDTO()
            .contentLabel(toDelete.getContentLabel())
            .name(toDelete.getName());

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class))
            .toList();

        List<ContentOverrideDTO> actual = resource
            .deleteEnvironmentContentOverrides(environment.getId(), List.of(toDeleteDTO))
            .toList();

        assertThat(actual)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testDeleteOverridesUsingContentLabel() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();
        List<EnvironmentContentOverride> overrides = this.createOverrides(environment, 1, 3);

        EnvironmentContentOverride toDelete = overrides.remove(1);
        ContentOverrideDTO toDeleteDTO = new ContentOverrideDTO()
            .contentLabel(toDelete.getContentLabel());

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class))
            .toList();

        List<ContentOverrideDTO> actual = resource
            .deleteEnvironmentContentOverrides(environment.getId(), List.of(toDeleteDTO))
            .toList();

        assertThat(actual)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyList() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();
        List<EnvironmentContentOverride> overrides = this.createOverrides(environment, 1, 3);

        List<ContentOverrideDTO> actual = resource
            .deleteEnvironmentContentOverrides(environment.getId(), List.of())
            .toList();

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyContentLabel() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();
        List<EnvironmentContentOverride> overrides = this.createOverrides(environment, 1, 3);

        List<ContentOverrideDTO> actual = resource
            .deleteEnvironmentContentOverrides(environment.getId(), List.of())
            .toList();

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testAddOverride() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();

        ContentOverrideDTO dto1 = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        List<ContentOverrideDTO> output1 = resource
            .putEnvironmentContentOverrides(environment.getId(), List.of(dto1))
            .toList();

        assertThat(output1)
            .hasSize(1)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrder(dto1);

        // Add a second to ensure we don't clobber the first
        ContentOverrideDTO dto2 = new ContentOverrideDTO()
            .contentLabel("test_label-2")
            .name("override_name-2")
            .value("override_value-2");

        List<ContentOverrideDTO> output2 = resource
            .putEnvironmentContentOverrides(environment.getId(), List.of(dto2))
            .toList();

        assertThat(output2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrder(dto1, dto2);
    }

    @Test
    public void testAddOverrideOverwritesExistingWhenMatched() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();

        ContentOverrideDTO dto1 = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        List<ContentOverrideDTO> output1 = resource
            .putEnvironmentContentOverrides(environment.getId(), List.of(dto1))
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

        List<ContentOverrideDTO> output2 = resource
            .putEnvironmentContentOverrides(environment.getId(), List.of(dto2))
            .toList();

        assertThat(output2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrder(dto2);
    }

    @Test
    public void testAddOverrideFailsValidationWithNoParent() {
        EnvironmentResource resource = this.buildResource();

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.putEnvironmentContentOverrides(null, List.of(dto)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testAddOverrideFailsValidationWithNullOrEmptyLabel(String label) {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(label)
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.putEnvironmentContentOverrides(environment.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithLongLabel() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();

        String longString = "a".repeat(ContentOverride.MAX_NAME_AND_LABEL_LENGTH + 1);

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(longString)
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.putEnvironmentContentOverrides(environment.getId(), List.of(dto)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testAddOverrideFailsValidationWithNullOrEmptyName(String name) {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(name)
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.putEnvironmentContentOverrides(environment.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithLongName() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();

        String longString = "a".repeat(ContentOverride.MAX_NAME_AND_LABEL_LENGTH + 1);

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(longString)
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.putEnvironmentContentOverrides(environment.getId(), List.of(dto)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testAddOverrideFailsValidationWithNullOrEmptyValue(String value) {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(value);

        assertThrows(BadRequestException.class,
            () -> resource.putEnvironmentContentOverrides(environment.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithLongValue() {
        EnvironmentResource resource = this.buildResource();
        Environment environment = this.createEnvironment();

        String longString = "a".repeat(ContentOverride.MAX_VALUE_LENGTH + 1);

        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(longString);

        assertThrows(BadRequestException.class,
            () -> resource.putEnvironmentContentOverrides(environment.getId(), List.of(dto)));
    }
}
