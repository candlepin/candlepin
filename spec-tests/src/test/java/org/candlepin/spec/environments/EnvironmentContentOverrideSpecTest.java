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
package org.candlepin.spec.environments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;

import org.candlepin.dto.api.client.v1.ContentOverrideDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ContentOverrides;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;



@SpecTest
public class EnvironmentContentOverrideSpecTest {

    private ListAssert<ContentOverrideDTO> assertEnvironmentContentOverrides(ApiClient client,
        EnvironmentDTO environment) {

        List<ContentOverrideDTO> overrides = client.environments()
            .getEnvironmentContentOverrides(environment.getId());

        return assertThat(overrides);
    }

    private EnvironmentDTO createEnvironment(ApiClient client) {
        OwnerDTO owner = client.owners()
            .createOwner(Owners.random());

        return client.owners()
            .createEnvironment(owner.getKey(), Environments.random());
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenCreatingConsumerContentOverrides() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        List<ContentOverrideDTO> overrides = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(ContentOverrides.random()));

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(overrides)
            .hasSize(1);

        ContentOverrideDTO output = overrides.get(0);

        assertThat(output.getCreated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(output.getUpdated());

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldAllowCreatingEnvironmentContentOverrides() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random();
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2));

        assertThat(overrides1)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2));

        assertEnvironmentContentOverrides(adminClient, environment)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2));

        Thread.sleep(1100);

        // Add a third element to ensure the result is the union of all overrides submitted thus far
        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co3));

        assertThat(overrides2)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3));

        assertEnvironmentContentOverrides(adminClient, environment)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3));

        // Verify that the last update time has changed as a result
        EnvironmentDTO updated = adminClient.environments().getEnvironment(environment.getId());

        assertThat(updated.getCreated())
            .isEqualTo(environment.getCreated())
            .isBefore(updated.getUpdated());

        assertThat(updated.getUpdated())
            .isAfter(environment.getUpdated())
            .isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldThrowBadRequestOnCreationWithNullOrEmptyLabel(String label) {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random()
            .contentLabel(label);
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();

        assertBadRequest(() -> adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3)));

        assertEnvironmentContentOverrides(adminClient, environment)
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldThrowBadRequestOnCreationWithNullOrEmptyAttribute(String attrib) {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random()
            .name(attrib);
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();

        assertBadRequest(() -> adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3)));

        assertEnvironmentContentOverrides(adminClient, environment)
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldThrowBadRequestOnCreationWithNullOrEmptyValue(String value) {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random()
            .value(value);
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();

        assertBadRequest(() -> adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3)));

        assertEnvironmentContentOverrides(adminClient, environment)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void shouldThrowNotFoundExceptionWithInvalidEnvironmentIdOnFetch() {
        ApiClient adminClient = ApiClients.admin();

        assertNotFound(() -> adminClient.environments()
            .getEnvironmentContentOverrides("badEnvId"));
    }

    @Test
    public void shouldAllowUpdatingEnvironmentContentOverrides() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random();
        ContentOverrideDTO co2 = ContentOverrides.random();

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2));

        assertThat(overrides1)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2));

        Thread.sleep(1100);

        // Update the value on one of our content overrides and submit it
        ContentOverrideDTO co3 = new ContentOverrideDTO()
            .name(co2.getName())
            .contentLabel(co2.getContentLabel())
            .value("updated_value");

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co3));

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co3))
            .doesNotContain(co2);

        assertEnvironmentContentOverrides(adminClient, environment)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co3))
            .doesNotContain(co2);

        // Verify that the last update time has changed as a result
        EnvironmentDTO updated = adminClient.environments().getEnvironment(environment.getId());

        assertThat(updated.getCreated())
            .isEqualTo(environment.getCreated())
            .isBefore(updated.getUpdated());

        assertThat(updated.getUpdated())
            .isAfter(environment.getUpdated())
            .isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    public void shouldNotShareContentOverridesBetweenEnvironments() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        EnvironmentDTO environment1 = adminClient.owners()
            .createEnvironment(owner1.getKey(), Environments.random());
        EnvironmentDTO environment2 = adminClient.owners()
            .createEnvironment(owner1.getKey(), Environments.random());
        EnvironmentDTO environment3 = adminClient.owners()
            .createEnvironment(owner2.getKey(), Environments.random());

        ContentOverrideDTO co1 = ContentOverrides.random();
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment1.getId(), List.of(co1));

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .putEnvironmentContentOverrides(environment2.getId(), List.of(co2));

        List<ContentOverrideDTO> overrides3 = adminClient.environments()
            .putEnvironmentContentOverrides(environment3.getId(), List.of(co3));

        assertThat(overrides1)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co1);

        assertThat(overrides2)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co2);

        assertThat(overrides3)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co3);

        assertEnvironmentContentOverrides(adminClient, environment1)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co1);

        assertEnvironmentContentOverrides(adminClient, environment2)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co2);

        assertEnvironmentContentOverrides(adminClient, environment3)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "NAME", "NAme", "label", "LABEL", "LaBeL"})
    public void shouldRejectChangesForProtectedAttributes(String attribute) {
        ApiClient adminClient = ApiClients.admin();

        EnvironmentDTO environment = this.createEnvironment(adminClient);
        ContentOverrideDTO override = ContentOverrides.random()
            .name(attribute);

        assertBadRequest(() -> adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(override)));

        assertEnvironmentContentOverrides(adminClient, environment)
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "NAME", "NAme", "label", "LABEL", "LaBeL"})
    public void shouldRejectAllChangesIfAnyOverrideContainsProtectedAttributes(String attribute) {
        ApiClient adminClient = ApiClients.admin();

        EnvironmentDTO environment = this.createEnvironment(adminClient);
        ContentOverrideDTO co1 = ContentOverrides.random();
        ContentOverrideDTO co2 = ContentOverrides.random().name(attribute);
        ContentOverrideDTO co3 = ContentOverrides.random();

        assertBadRequest(() -> adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3)));

        assertEnvironmentContentOverrides(adminClient, environment)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void shouldUpdateContentOverrideValueForAttributeWithDifferentCase() {
        ApiClient adminClient = ApiClients.admin();

        EnvironmentDTO environment = this.createEnvironment(adminClient);
        ContentOverrideDTO co1 = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override")
            .value("value");
        ContentOverrideDTO co2 = new ContentOverrideDTO()
            .contentLabel(co1.getContentLabel())
            .name("OvErRiDE")
            .value("updated_value");
        ContentOverrideDTO expected = new ContentOverrideDTO()
            .contentLabel(co1.getContentLabel())
            .name(co1.getName())
            .value(co2.getValue());

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1));

        assertThat(overrides1)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co1);

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co2));

        assertThat(overrides2)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(expected);

        assertEnvironmentContentOverrides(adminClient, environment)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(expected);
    }

    @Test
    public void shouldUseLastValueWhenDuplicateValuesAreProvided() {
        ApiClient adminClient = ApiClients.admin();

        EnvironmentDTO environment = this.createEnvironment(adminClient);
        ContentOverrideDTO co1 = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override")
            .value("value");
        ContentOverrideDTO co2 = new ContentOverrideDTO()
            .contentLabel(co1.getContentLabel())
            .name("OvErRiDE")
            .value("updated_value");
        ContentOverrideDTO expected = new ContentOverrideDTO()
            .contentLabel(co1.getContentLabel())
            .name(co1.getName())
            .value(co2.getValue());

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2));

        assertThat(overrides1)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(expected);

        assertEnvironmentContentOverrides(adminClient, environment)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldAllowDeletingAllEnvironmentContentOverridesWithNullOrEmptyList(
        List<ContentOverrideDTO> input) throws Exception {

        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random();
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3));

        assertThat(overrides1)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3));

        Thread.sleep(1100);

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment.getId(), input);

        assertThat(overrides2)
            .isNotNull()
            .isEmpty();

        assertEnvironmentContentOverrides(adminClient, environment)
            .isNotNull()
            .isEmpty();

        // Verify that the last update time has changed as a result
        EnvironmentDTO updated = adminClient.environments().getEnvironment(environment.getId());

        assertThat(updated.getCreated())
            .isEqualTo(environment.getCreated())
            .isBefore(updated.getUpdated());

        assertThat(updated.getUpdated())
            .isAfter(environment.getUpdated())
            .isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldNotAffectOtherEnvironmentsWhenDeletingAllOverridesWithNullOrEmptyList(
        List<ContentOverrideDTO> input) {

        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment1 = this.createEnvironment(adminClient);
        EnvironmentDTO environment2 = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random();
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();
        ContentOverrideDTO co4 = ContentOverrides.random();

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment1.getId(), List.of(co1, co2));

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .putEnvironmentContentOverrides(environment2.getId(), List.of(co3, co4));

        assertThat(overrides1)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2));

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co3, co4));

        List<ContentOverrideDTO> overrides3 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment1.getId(), input);

        assertThat(overrides3)
            .isNotNull()
            .isEmpty();

        assertEnvironmentContentOverrides(adminClient, environment1)
            .isNotNull()
            .isEmpty();

        assertEnvironmentContentOverrides(adminClient, environment2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co3, co4));
    }

    @Test
    public void shouldAllowDeletingAllEnvironmentContentOverridesWithLabellessElement() {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random();
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3));

        assertThat(overrides1)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3));

        ContentOverrideDTO toDelete = new ContentOverrideDTO()
            .name("attrib")
            .value("value");

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment.getId(), List.of(toDelete));

        assertThat(overrides2)
            .isNotNull()
            .isEmpty();

        assertEnvironmentContentOverrides(adminClient, environment)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void shouldNotAffectOtherEnvironmentsWhenDeletingAllOverridesWithLabellessElement() {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment1 = this.createEnvironment(adminClient);
        EnvironmentDTO environment2 = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random();
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();
        ContentOverrideDTO co4 = ContentOverrides.random();

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment1.getId(), List.of(co1, co2));

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .putEnvironmentContentOverrides(environment2.getId(), List.of(co3, co4));

        assertThat(overrides1)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2));

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co3, co4));

        ContentOverrideDTO toDelete = new ContentOverrideDTO()
            .name("attrib")
            .value("value");

        List<ContentOverrideDTO> overrides3 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment1.getId(), List.of(toDelete));

        assertThat(overrides3)
            .isNotNull()
            .isEmpty();

        assertEnvironmentContentOverrides(adminClient, environment1)
            .isNotNull()
            .isEmpty();

        assertEnvironmentContentOverrides(adminClient, environment2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co3, co4));
    }

    @Test
    public void shouldAllowDeletingMultipleEnvironmentContentOverridesByLabel() {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random()
            .contentLabel("label1");
        ContentOverrideDTO co2 = ContentOverrides.random()
            .contentLabel("label1");
        ContentOverrideDTO co3 = ContentOverrides.random()
            .contentLabel("label2");

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3));

        assertThat(overrides1)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3));

        ContentOverrideDTO toDelete = new ContentOverrideDTO()
            .contentLabel("label1");

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment.getId(), List.of(toDelete));

        assertThat(overrides2)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co3);

        assertEnvironmentContentOverrides(adminClient, environment)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co3);
    }

    @Test
    public void shouldNotAffectOtherEnvironmentsWhenDeletingMultipleOverridesByLabel() {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment1 = this.createEnvironment(adminClient);
        EnvironmentDTO environment2 = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random()
            .contentLabel("label1");
        ContentOverrideDTO co2 = ContentOverrides.random()
            .contentLabel("label2");
        ContentOverrideDTO co3 = ContentOverrides.random()
            .contentLabel("label1");
        ContentOverrideDTO co4 = ContentOverrides.random()
            .contentLabel("label2");

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment1.getId(), List.of(co1, co2));

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .putEnvironmentContentOverrides(environment2.getId(), List.of(co3, co4));

        assertThat(overrides1)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2));

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co3, co4));

        ContentOverrideDTO toDelete = new ContentOverrideDTO()
            .contentLabel("label1");

        List<ContentOverrideDTO> overrides3 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment1.getId(), List.of(toDelete));

        assertThat(overrides3)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co2);

        assertEnvironmentContentOverrides(adminClient, environment1)
            .singleElement()
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(co2);

        assertEnvironmentContentOverrides(adminClient, environment2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co3, co4))
            .doesNotContain(co1);
    }

    @Test
    public void shouldAllowDeletingMultipleEnvironmentContentOverridesByAttribute() {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random()
            .contentLabel("label1")
            .name("attrib1");
        ContentOverrideDTO co2 = ContentOverrides.random()
            .contentLabel("label2")
            .name("attrib1");
        ContentOverrideDTO co3 = ContentOverrides.random()
            .contentLabel("label2")
            .name("attrib2");

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3));

        assertThat(overrides1)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3));

        ContentOverrideDTO toDelete = new ContentOverrideDTO()
            .contentLabel("label1")
            .name("attrib1");

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment.getId(), List.of(toDelete));

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co2, co3))
            .doesNotContain(co1);

        assertEnvironmentContentOverrides(adminClient, environment)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co2, co3))
            .doesNotContain(co1);
    }

    @Test
    public void shouldNotAffectOtherEnvironmentsWhenDeletingMultipleOverridesByAttribute() {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment1 = this.createEnvironment(adminClient);
        EnvironmentDTO environment2 = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random()
            .contentLabel("label1")
            .name("attrib1");
        ContentOverrideDTO co2 = ContentOverrides.random()
            .contentLabel("label2")
            .name("attrib1");
        ContentOverrideDTO co3 = ContentOverrides.random()
            .contentLabel("label1")
            .name("attrib1");
        ContentOverrideDTO co4 = ContentOverrides.random()
            .contentLabel("label2")
            .name("attrib1");

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment1.getId(), List.of(co1, co2));

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .putEnvironmentContentOverrides(environment2.getId(), List.of(co3, co4));

        assertThat(overrides1)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2));

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co3, co4));

        ContentOverrideDTO toDelete = new ContentOverrideDTO()
            .contentLabel("label1")
            .name("attrib1");

        List<ContentOverrideDTO> overrides3 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment1.getId(), List.of(toDelete));

        assertThat(overrides3)
            .hasSize(1)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co2))
            .doesNotContain(co1, co3, co4);

        assertEnvironmentContentOverrides(adminClient, environment1)
            .hasSize(1)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co2))
            .doesNotContain(co1, co3, co4);

        assertEnvironmentContentOverrides(adminClient, environment2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co3, co4));
    }

    @Test
    public void shouldNotChangeEnvironmentWithMismatchedContentOverride() {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random();
        ContentOverrideDTO co2 = ContentOverrides.random();
        ContentOverrideDTO co3 = ContentOverrides.random();

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3));

        assertThat(overrides1)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3));

        // This should not match at all with the overrides created above
        ContentOverrideDTO toDelete = ContentOverrides.random();

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment.getId(), List.of(toDelete));

        assertThat(overrides2)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3));

        assertEnvironmentContentOverrides(adminClient, environment)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3));
    }

    @Test
    public void shouldAllowDeletingEnvironmentContentOverridesWithMultipleCriteria() {
        ApiClient adminClient = ApiClients.admin();
        EnvironmentDTO environment = this.createEnvironment(adminClient);

        ContentOverrideDTO co1 = ContentOverrides.random()
            .contentLabel("label1");
        ContentOverrideDTO co2 = ContentOverrides.random()
            .contentLabel("label1");
        ContentOverrideDTO co3 = ContentOverrides.random()
            .contentLabel("label2")
            .name("attrib1");
        ContentOverrideDTO co4 = ContentOverrides.random()
            .contentLabel("label2")
            .name("attrib2");
        ContentOverrideDTO co5 = ContentOverrides.random()
            .contentLabel("label3")
            .name("attrib3");

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2, co3, co4, co5));

        assertThat(overrides1)
            .hasSize(5)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co1, co2, co3, co4, co5));

        ContentOverrideDTO deleteByLabel = new ContentOverrideDTO()
            .contentLabel("label1");
        ContentOverrideDTO deleteByAttrib = new ContentOverrideDTO()
            .contentLabel("label2")
            .name("attrib1");
        ContentOverrideDTO mismatched = ContentOverrides.random();

        List<ContentOverrideDTO> toDelete = List.of(deleteByLabel, deleteByAttrib, mismatched);

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .deleteEnvironmentContentOverrides(environment.getId(), toDelete);

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co4, co5));

        assertEnvironmentContentOverrides(adminClient, environment)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(co4, co5));
    }
}
