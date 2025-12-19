/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertGone;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Permissions;
import org.candlepin.spec.bootstrap.data.builder.Roles;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;


@SpecTest
public class ConsumerResourceSpecTest {
    private static final String LIST_CONSUMERS_PATH = "/owners/{owner_key}/consumers";

    private ApiClient adminClient;
    private OwnerDTO owner;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenCreatingConsumers() {
        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        ConsumerDTO output = this.adminClient.consumers().createConsumer(Consumers.random(this.owner));

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getId())
            .isNotNull()
            .isNotBlank();

        assertThat(output.getCreated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(post);
    }

    public static Stream<Arguments> cloudFactsSource() {
        List<String> awsFacts = List.of("aws_instance_id", "aws_account_id", "aws_billing_products",
            "aws_marketplace_product_codes");

        List<String> azureFacts = List.of("azure_instance_id", "azure_subscription_id", "azure_offer");

        List<String> gcpFacts = List.of("gcp_instance_id", "gcp_project_id", "gcp_license_codes");

        return Stream.of(
            Arguments.of(awsFacts),
            Arguments.of(azureFacts),
            Arguments.of(gcpFacts));
    }

    private void registerWithCloudFacts(List<String> facts, String factValue) throws Exception {
        OwnerDTO owner = this.adminClient.owners().createOwner(Owners.random());

        ConsumerDTO consumer = Consumers.random(owner);
        facts.forEach(fact -> consumer.putFactsItem(fact, factValue));

        // Impl note: We have to manually serialize this and create our own request to avoid an issue with
        // the GSON serializer being helpful and throwing out null values on our behalf against our wishes.
        Response response = Request.from(this.adminClient)
            .setPath("/consumers")
            .setMethod("POST")
            .addQueryParam("owner", owner.getKey())
            .setBody(ApiClient.MAPPER.writeValueAsString(consumer))
            .execute();

        assertEquals(200, response.getCode());
    }

    @ParameterizedTest
    @MethodSource("cloudFactsSource")
    public void shouldAllowRegistrationWithValidDataInCloudFacts(List<String> facts) throws Exception {
        this.registerWithCloudFacts(facts, "value");
    }

    @ParameterizedTest
    @MethodSource("cloudFactsSource")
    public void shouldAllowRegistrationWithNullDataInCloudFacts(List<String> facts) throws Exception {
        this.registerWithCloudFacts(facts, null);
    }

    @ParameterizedTest
    @MethodSource("cloudFactsSource")
    public void shouldAllowRegistrationWithEmptyDataInCloudFacts(List<String> facts) throws Exception {
        this.registerWithCloudFacts(facts, "");
    }

    private void registerAndCheckinWithCloudFacts(List<String> facts, String factValue) throws Exception {
        OwnerDTO owner = this.adminClient.owners().createOwner(Owners.random());

        ConsumerDTO consumer = Consumers.random(owner);
        facts.forEach(fact -> consumer.putFactsItem(fact, factValue));

        // Impl note: We have to manually serialize this and create our own request to avoid an issue with
        // the GSON serializer being helpful and throwing out null values on our behalf against our wishes.
        Response response = Request.from(this.adminClient)
            .setPath("/consumers")
            .setMethod("POST")
            .addQueryParam("owner", owner.getKey())
            .setBody(ApiClient.MAPPER.writeValueAsString(consumer))
            .execute();

        assertEquals(200, response.getCode());

        ConsumerDTO registeredConsumer = response.deserialize(ConsumerDTO.class);
        ApiClient consumerClient = ApiClients.ssl(registeredConsumer);

        // Impl note:
        // we're not interested in the output so much as ensuring we can do this without triggering an
        // exception. The exact operation here isn't even important, we just need to hit something annotated
        // with @UpdateConsumerCheckIn so the filter is invoked
        consumerClient.consumers().updateConsumer(registeredConsumer.getUuid(), registeredConsumer);
    }

    @ParameterizedTest
    @MethodSource("cloudFactsSource")
    public void shouldAllowCheckinOpsAfterRegistrationWithValidDataInCloudFacts(List<String> facts)
        throws Exception {

        this.registerAndCheckinWithCloudFacts(facts, "value");
    }

    @ParameterizedTest
    @MethodSource("cloudFactsSource")
    public void shouldAllowCheckinOpsAfterRegistrationWithNullDataInCloudFacts(List<String> facts)
        throws Exception {

        this.registerAndCheckinWithCloudFacts(facts, null);
    }

    @ParameterizedTest
    @MethodSource("cloudFactsSource")
    public void shouldAllowCheckinOpsAfterRegistrationWithEmptyDataInCloudFacts(List<String> facts)
        throws Exception {

        this.registerAndCheckinWithCloudFacts(facts, "");
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenUpdatingConsumers() throws Exception {
        ConsumerDTO entity = this.adminClient.consumers().createConsumer(Consumers.random(this.owner));

        Thread.sleep(1100);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        entity.setName(entity.getName() + "-update");
        this.adminClient.consumers().updateConsumer(entity.getUuid(), entity);
        ConsumerDTO output = this.adminClient.consumers().getConsumer(entity.getUuid());

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getCreated())
            .isNotNull()
            .isEqualTo(entity.getCreated())
            .isBeforeOrEqualTo(init);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfter(output.getCreated())
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldCreateGuestWhenUpdatingConsumerWithGuestIdObject() throws Exception {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        String expectedGuestId = StringUtil.random("guestId-");
        Map<String, String> expectedAttributes = Map.of(StringUtil.random(5), StringUtil.random(5),
            StringUtil.random(5), StringUtil.random(5));
        GuestIdDTO guestId = new GuestIdDTO()
            .guestId(expectedGuestId)
            .attributes(expectedAttributes);
        consumer.setGuestIds(List.of(guestId));
        ReleaseVerDTO releaseVer = new ReleaseVerDTO()
            .releaseVer("");
        consumer.setReleaseVer(releaseVer);
        adminClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        GuestIdDTO actual = adminClient.guestIds().getGuestId(consumer.getUuid(), expectedGuestId);
        assertThat(actual)
            .isNotNull()
            .returns(expectedGuestId, GuestIdDTO::getGuestId)
            .returns(expectedAttributes, GuestIdDTO::getAttributes);
    }

    @Test
    public void shouldCreateGuestWhenUpdatingConsumerWithGuestIdString() throws Exception {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ReleaseVerDTO releaseVer = new ReleaseVerDTO()
            .releaseVer("");
        consumer.setReleaseVer(releaseVer);
        JsonNode consumerRoot = ApiClient.MAPPER.valueToTree(consumer);
        ObjectNode objectNode = (ObjectNode) consumerRoot;

        ArrayNode arrayNode = ApiClient.MAPPER.createArrayNode();
        String expectedGuestId1 = StringUtil.random("guest-");
        String expectedGuestId2 = StringUtil.random("guest-");
        arrayNode.add(expectedGuestId1);
        arrayNode.add(expectedGuestId2);
        objectNode.putPOJO("guestIds", arrayNode);

        // ObjectMapper.valueToTree converts the OffsetDateTimes to epoch values which
        // is not acceped by the server, so this is cleaning up the json to be accepted.
        objectNode.put("created", consumer.getCreated().toString());
        objectNode.put("updated", consumer.getUpdated().toString());
        ObjectNode nullNode = null;
        objectNode.set("lastCheckin", nullNode);
        objectNode.set("idCert", nullNode);
        objectNode.set("serial", nullNode);

        Response response = Request.from(adminClient)
            .setPath("/consumers/{consumer_uuid}")
            .setMethod("PUT")
            .setPathParam("consumer_uuid", consumer.getUuid())
            .setBody(objectNode.toString())
            .execute();

        assertThat(response)
            .isNotNull()
            .returns(204, Response::getCode);

        GuestIdDTO actual = adminClient.guestIds().getGuestId(consumer.getUuid(), expectedGuestId1);
        assertThat(actual)
            .isNotNull()
            .returns(expectedGuestId1, GuestIdDTO::getGuestId);

        actual = adminClient.guestIds().getGuestId(consumer.getUuid(), expectedGuestId2);
        assertThat(actual)
            .isNotNull()
            .returns(expectedGuestId2, GuestIdDTO::getGuestId);
    }

    @Test
    public void shouldNotAllowSettingEntitlementCountOnRegister() {
        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).entitlementCount(3L));

        assertThat(consumer)
            .isNotNull()
            .returns(0L, ConsumerDTO::getEntitlementCount);
    }

    @Test
    public void shouldNotAllowCopyingIdCertToOtherConsumers() {
        ConsumerDTO initialConsumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        CertificateDTO expectedIdCert = initialConsumer.getIdCert();

        ConsumerDTO newConsumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).idCert(expectedIdCert));

        assertThat(newConsumer)
            .isNotNull()
            .doesNotReturn(expectedIdCert, ConsumerDTO::getIdCert);
    }

    @Test
    public void shouldReceivePagedConsumersBackWhenRequested() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer4 = adminClient.consumers().createConsumer(Consumers.random(owner));

        Response response = Request.from(adminClient)
            .setPath(LIST_CONSUMERS_PATH)
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "1")
            .addQueryParam("per_page", "1")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ConsumerDTOArrayElement>>() {}))
            .singleElement()
            .returns(consumer1.getId(), ConsumerDTOArrayElement::getId);

        response = Request.from(adminClient)
            .setPath(LIST_CONSUMERS_PATH)
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "2")
            .addQueryParam("per_page", "1")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ConsumerDTOArrayElement>>() {}))
            .singleElement()
            .returns(consumer2.getId(), ConsumerDTOArrayElement::getId);

        response = Request.from(adminClient)
            .setPath(LIST_CONSUMERS_PATH)
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "3")
            .addQueryParam("per_page", "1")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ConsumerDTOArrayElement>>() {}))
            .singleElement()
            .returns(consumer3.getId(), ConsumerDTOArrayElement::getId);

        response = Request.from(adminClient)
            .setPath(LIST_CONSUMERS_PATH)
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "4")
            .addQueryParam("per_page", "1")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ConsumerDTOArrayElement>>() {}))
            .singleElement()
            .returns(consumer4.getId(), ConsumerDTOArrayElement::getId);
    }

    @Test
    public void shouldListCompliances() {
        UserDTO user1 = createUserTypeAllAccess(adminClient, owner);
        ApiClient user1Client = ApiClients.basic(user1);
        ConsumerDTO consumer1 = user1Client.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = user1Client.consumers().createConsumer(Consumers.random(owner));

        Map<String, ComplianceStatusDTO> compliance = user1Client.consumers()
            .getComplianceStatusList(List.of(consumer1.getUuid(), consumer2.getUuid()));

        assertThat(compliance)
            .hasSize(2)
            .containsKeys(consumer1.getUuid(), consumer2.getUuid());
    }

    @Test
    public void shouldFilterCompliancesTheUserDoesNotOwn() {
        UserDTO user1 = createUserTypeAllAccess(adminClient, owner);
        ApiClient user1Client = ApiClients.basic(user1);
        ConsumerDTO consumer1 = user1Client.consumers().createConsumer(Consumers.random(owner));

        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner2));

        Map<String, ComplianceStatusDTO> compliance = user1Client.consumers()
            .getComplianceStatusList(List.of(consumer1.getUuid(), consumer2.getUuid()));

        assertThat(compliance)
            .hasSize(1)
            .containsKeys(consumer1.getUuid());
    }

    @Test
    public void shouldReturnGoneStatusCodeForDeletedConsumers() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().deleteConsumer(consumer.getUuid());

        assertGone(() -> adminClient.consumers().getConsumer(consumer.getUuid()));
    }

    @Test
    public void shouldReturnAGoneStatusForAConsumerWithAnInvalidIdentityCert() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().deleteConsumer(consumer.getUuid());

        assertGone(() -> consumerClient.consumers().deleteConsumer(consumer.getUuid()));
    }

    @Test
    public void shouldAllowSuperAdminsToSeeAllConsumers() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner2));

        List<String> uuids = List.of(consumer1.getUuid(), consumer2.getUuid());
        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(null, Set.of("system"), null, uuids, null, null,
                null, null, null, null, null, null);

        assertThat(consumers)
            .extracting(ConsumerDTOArrayElement::getUuid)
            .contains(consumer1.getUuid(), consumer2.getUuid());
    }

    @Test
    public void shouldAllowSuperAdminsToQueryConsumersById() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner2));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(Consumers.random(owner));

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(null, null, null, List.of(consumer1.getUuid(), consumer2.getUuid()), null, null,
            null, null, null, null, null, null);

        assertThat(consumers)
            .extracting(ConsumerDTOArrayElement::getUuid)
            .contains(consumer1.getUuid(), consumer2.getUuid())
            .doesNotContain(consumer3.getUuid());
    }

    @Test
    public void shouldLetASuperAdminFilterConsumerByOwner() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(null, null, owner.getKey(), null, null, null,
                null, null, null, null, null, null);

        assertThat(consumers)
            .singleElement()
            .returns(consumer.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldLetASuperAdminSeeAPersonConsumerWithAGivenUsername() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner));
        adminClient.roles().addUserToRole(role.getName(), user.getUsername());
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createPersonConsumer(Consumers.random(owner)
            .type(ConsumerTypes.Person.value()), user.getUsername());

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(user.getUsername(), Set.of("person"), null, null, null, null, null, null, null,
            null, null, null);

        assertThat(consumers)
            .singleElement()
            .returns(consumer.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldLetASuperAdminCreatePersonConsumerForAnotherUser() {
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        UserDTO user = createUserTypeAllAccess(adminClient, owner2);
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner2));
        adminClient.roles().addUserToRole(role.getName(), user.getUsername());
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createPersonConsumer(Consumers.random(owner2)
            .type(ConsumerTypes.Person.value()), user.getUsername());

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(user.getUsername(), Set.of("person"), owner2.getKey(), null, null, null, null,
            null, null, null, null, null);

        assertThat(consumers)
            .singleElement()
            .returns(consumer.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldNotAllowAnOwnerAdminCreatePersonConsumerForAnotherOwner() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner));
        adminClient.roles().addUserToRole(role.getName(), user.getUsername());
        ApiClient userClient = ApiClients.basic(user);
        userClient.consumers().createPersonConsumer(Consumers.random(owner)
            .type(ConsumerTypes.Person.value()), user.getUsername());

        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        UserDTO user2 = createUserTypeAllAccess(adminClient, owner2);
        RoleDTO role2 = adminClient.roles().createRole(Roles.ownerAll(owner2));
        adminClient.roles().addUserToRole(role2.getName(), user2.getUsername());
        adminClient.consumers().createConsumer(Consumers.random(owner2));
        ApiClient user2Client = ApiClients.basic(user2);

        assertNotFound(() -> user2Client.consumers().createPersonConsumer(Consumers.random(owner)
            .type(ConsumerTypes.Person.value()), user.getUsername()));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"", "#something", "bar$%camp", "문자열이 아님"})
    public void shouldNotAllowSuperAdminToCreateConsumerWithInvalidName(String name) {
        assertBadRequest(() -> adminClient.consumers()
            .createConsumer(Consumers.random(owner).name(name)));
    }

    @Test
    public void shouldNotAllowUserToCreateConsumerInOrganizationWithoutPermission() {
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());

        // We need a user with the USERNAME_CONSUMERS - ALL permission, which allows for
        // create/view/update/delete of consumers, on a specific Organization ('owner').
        UserDTO user = UserUtil.createWith(ApiClients.admin(), Permissions.USERNAME_CONSUMERS.all(owner));

        // This user should NOT be able to create consumers in a different organization ('owner2').
        // Instead, a 404 should be returned, that tells the user that an org with that key does not exist
        // in order to not confirm or deny its existence.
        ApiClient userClient = ApiClients.basic(user);
        assertNotFound(() -> userClient.consumers().createConsumer(Consumers.random(owner2)));
    }

    @Test
    public void shouldNotAllowUserToViewUpdateOrDeleteConsumerInOrganizationWithoutPermission() {
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumerInOwner2 = adminClient.consumers().createConsumer(Consumers.random(owner2));

        // We need a user with the USERNAME_CONSUMERS - ALL permission, which allows for
        // create/view/update/delete of consumers, on a specific Organization ('owner').
        UserDTO user = UserUtil.createWith(ApiClients.admin(), Permissions.USERNAME_CONSUMERS.all(owner));

        // This user should NOT be able to view/update/delete consumers in a different organization
        // ('owner2'). Instead, a 404 should be returned, that tells the user that an org with that
        // key does not exist in order to not confirm or deny its existence.
        ApiClient userClient = ApiClients.basic(user);
        assertNotFound(() -> userClient.consumers().getConsumer(consumerInOwner2.getUuid()));

        consumerInOwner2.setName("newName");
        assertNotFound(() -> userClient.consumers().updateConsumer(consumerInOwner2.getUuid(),
            consumerInOwner2));

        assertNotFound(() -> userClient.consumers().deleteConsumer(consumerInOwner2.getUuid()));
    }

    @Test
    public void shouldReturnNotFoundForANonExistentConsumer() {
        adminClient.consumers().createConsumer(Consumers.random(owner));

        assertNotFound(() -> adminClient.consumers().getConsumer(StringUtil.random("unknown")));
    }

    @Test
    public void shouldReturnNotFoundWhenCheckingIfANonExistentConsumerExists() {
        adminClient.consumers().createConsumer(Consumers.random(owner));

        assertNotFound(() -> adminClient.consumers().consumerExists(StringUtil.random("unknown")));
    }

    @Test
    public void shouldReturnA204WhenCheckingIfARealConsumerExists() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        Response response = Request.from(adminClient)
            .setPath("/consumers/{consumer_uuid}/exists")
            .setMethod("HEAD")
            .setPathParam("consumer_uuid", consumer.getUuid())
            .execute();

        assertThat(response)
            .isNotNull()
            .returns(204, Response::getCode);
    }

    @Test
    public void shouldReturnA410WhenCheckingIfADeletedConsumerExists() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        adminClient.consumers().deleteConsumer(consumer.getUuid());
        StatusCodeAssertions.assertThatStatus(() ->
            adminClient.consumers().consumerExists(consumer.getUuid()))
            .isGone();
    }

    @Test
    public void shouldAllowConsumerToCheckForSelfExistence() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().consumerExists(consumer.getUuid());
    }

    @Test
    public void shouldNotAllowConsumerToCheckExistenceOfOtherConsumers() {
        // This test should expect a 404 rather than a 503, as we're explicitly minimizing the amount
        //  of information provided in the no-permission case. Consumer 1 has no access to consumer 2,
        //  and should not be able to determine whether consumer 2 even exists.
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer1);
        assertNotFound(() -> consumerClient.consumers().consumerExists(consumer2.getUuid()));
    }

    @Test
    public void shouldAllowAdminToCheckIfAnyConsumerExists() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        adminClient.consumers().consumerExists(consumer1.getUuid());
        adminClient.consumers().consumerExists(consumer2.getUuid());
    }

    @Test
    public void shouldNotAllowBulkCheckExistenceWhenNullInputIsProvided() {
        assertBadRequest(() -> adminClient.consumers().consumerExistsBulk(null));
    }

    @Test
    public void shouldReturnEmptyBodyWhenAllConsumerUuidExistsForBulkConsumerExistenceCheck() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        Response response = Request.from(adminClient)
            .setPath("/consumers/exists")
            .setMethod("POST")
            .setBody(List.of(consumer1.getUuid()))
            .execute();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    public void shouldRaiseResourceNotFoundWhenConsumerDoesNotExistForBulkConsumerExistenceCheck() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        Set consumerUuids = Set.of(consumer1.getUuid(), "test_uuid", "more_test_uuid");
        assertNotFound(() -> adminClient.consumers().consumerExistsBulk(consumerUuids));
    }

    @Test
    public void shouldReturnNonExistingIdsForBulkConsumerExistenceCheck() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        Set<String> consumerUuids = Set.of(consumer1.getUuid(), "test_uuid_1", "test_uuid_2");
        assertNotFound(() -> adminClient.consumers().consumerExistsBulk(consumerUuids))
            .hasMessageContainingAll("test_uuid_1", "test_uuid_2");
    }

    @Test
    public void shouldLetAConsumerViewTheirOwnInformation() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        assertThat(consumerClient.consumers().getConsumer(consumer.getUuid()))
            .returns(consumer.getUuid(), ConsumerDTO::getUuid);
    }

    @Test
    public void shouldNotLetAConsumerViewAnotherConsumersInformation() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer1 = userClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumer1Client = ApiClients.ssl(consumer1);
        assertNotFound(() -> consumer1Client.consumers().getConsumer(consumer2.getUuid()));
    }

    @Test
    public void shouldLetAConsumerRegisterWithContentTags() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));
        Set<String> tags = Set.of("awesomeos", "awesomeos-workstation", "otherproduct");
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).contentTags(tags));
        assertThat(consumer.getContentTags())
            .containsAll(tags);
    }

    @Test
    public void shouldLetAConsumerRegisterWithAnnotations() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));
        String annotations = "here is a piece of information, here is another piece.";
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).annotations(annotations));
        assertThat(consumer)
            .returns(annotations, ConsumerDTO::getAnnotations);
    }

    @Test
    public void shouldLetAConsumerRegisterAndSetCreatedAndLastCheckinDates() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));
        OffsetDateTime created = OffsetDateTime.now().minusDays(2L).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime checkin = OffsetDateTime.now().minusDays(1L).truncatedTo(ChronoUnit.SECONDS);

        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).created(created).lastCheckin(checkin));
        assertThat(consumer)
            .returns(true, x -> created.isEqual(x.getCreated()))
            .returns(true, x -> checkin.isEqual(x.getLastCheckin()));
        // reload to be sure it was persisted
        consumer = someUser.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .returns(true, x -> created.isEqual(x.getCreated()))
            .returns(true, x -> checkin.isEqual(x.getLastCheckin()));
    }

    @Test
    public void shouldLetAConsumerRegisterAndDisableAutoheal() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));

        String serviceLevel = "test_service_level";
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).autoheal(false));
        assertThat(consumer)
            .returns(false, ConsumerDTO::getAutoheal);
        // reload to be sure it was persisted
        consumer = someUser.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .returns(false, ConsumerDTO::getAutoheal);
    }

    @Test
    public void shouldAllowAConsumerToUpdateTheirAutohealFlag() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getAutoheal()).isTrue();

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().autoheal(false));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getAutoheal()).isFalse();

        // Null update shouldn't modify the setting:
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO());
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getAutoheal()).isFalse();
    }

    @Test
    public void shouldNotLetAnOwnerReregisterAnotherOwnersConsumer() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO linuxNet = ownerClient.createOwner(Owners.random());
        OwnerDTO greenfield = ownerClient.createOwner(Owners.random());

        ApiClient linuxBill = ApiClients.basic(UserUtil.createUser(adminClient, linuxNet));
        ApiClient greenRalph = ApiClients.basic(UserUtil.createUser(adminClient, greenfield));

        ConsumerDTO system1 = linuxBill.consumers().createConsumer(Consumers.random(linuxNet));
        assertNotFound(() -> greenRalph.consumers().regenerateIdentityCertificates(system1.getUuid()));
    }

    @Test
    public void shouldAllowConsumerToSpecifyTheirOwnUuid() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .uuid("custom-uuid");
        assertThat(consumer).returns("custom-uuid", ConsumerDTO::getUuid);
    }

    @Test
    public void shouldNotAllowTheSameUuidToBeRegisteredTwice() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        String testUuid = StringUtil.random("ALF");
        // Register the UUID initially
        userClient.consumers().createConsumer(Consumers.random(owner).uuid(testUuid));

        // Second registration should be denied
        assertBadRequest(() -> userClient.consumers().createConsumer(Consumers.random(owner).uuid(testUuid)));
    }

    @Test
    public void shouldNotLetAnOwnerRegisterWithUuidOfAnotherOwnersConsumer() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO linuxNet = ownerClient.createOwner(Owners.random());
        OwnerDTO greenfield = ownerClient.createOwner(Owners.random());

        ApiClient linuxBill = ApiClients.basic(UserUtil.createUser(adminClient, linuxNet));
        ApiClient greenRalph = ApiClients.basic(UserUtil.createUser(adminClient, greenfield));

        ConsumerDTO system1 = linuxBill.consumers().createConsumer(Consumers.random(linuxNet));
        assertBadRequest(() -> greenRalph.consumers().createConsumer(
            Consumers.random(greenfield).uuid(system1.getUuid())));
    }

    @Test
    public void shouldNotAllowTheSystemUuidToBeUsedToMatchAConsumerAcrossOrgs() {
        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        ApiClient userClient1 = ApiClients.basic(UserUtil.createUser(adminClient, owner1));
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ApiClient userClient2 = ApiClients.basic(UserUtil.createUser(adminClient, owner2));

        String hostName = StringUtil.random("hostname");
        String systemId = StringUtil.random("system");

        ConsumerDTO test1 = Consumers.random(owner1)
            .name(hostName)
            .facts(Map.of("dmi.system.uuid", systemId, "virt.is_guest", "false"));
        test1 = userClient1.consumers().createConsumer(test1);

        // different org should not use the same consumer record because of system uuid
        ConsumerDTO test2 = Consumers.random(owner2)
            .name(hostName)
            .facts(Map.of("dmi.system.uuid", systemId, "virt.is_guest", "false"));
        test2 = userClient2.consumers().createConsumer(test2);
        assertThat(adminClient.consumers().getConsumer(test2.getUuid()))
            .doesNotReturn(test1.getUuid(), ConsumerDTO::getUuid);

        // same org should use the same consumer record because of system uuid
        ConsumerDTO test3 = Consumers.random(owner1)
            .name(hostName)
            .facts(Map.of("dmi.system.uuid", systemId, "virt.is_guest", "false"));
        test3 = userClient1.consumers().createConsumer(test3);
        assertThat(adminClient.consumers().getConsumer(test3.getUuid()))
            .returns(test1.getUuid(), ConsumerDTO::getUuid);

    }

    @Test
    public void shouldAllowAConsumerToRegisterAndUpdateInstalledProducts() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        String pid1 = "918237";
        String pid2 = "871234";
        String pid3 = "712717";
        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(pid1).productName("My Installed Product"),
            new ConsumerInstalledProductDTO().productId(pid2).productName("Another Installed Product"));

        // Set a single fact, so we can make sure it doesn't get clobbered:
        Map<String, String> facts = Map.of("system.machine", "x86_64");
        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        assertThat(consumer.getInstalledProducts())
            .hasSize(2)
            .map(ConsumerInstalledProductDTO::getProductId)
            .containsAll(List.of(pid1, pid2));

        // Now update the installed packages:
        installed = Set.of(
            new ConsumerInstalledProductDTO().productId(pid1).productName("My Installed Product"),
            new ConsumerInstalledProductDTO().productId(pid3).productName("Third Installed Product"));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().installedProducts(installed));
        consumer = userClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getInstalledProducts())
            .hasSize(2)
            .map(ConsumerInstalledProductDTO::getProductId)
            .containsAll(List.of(pid1, pid3));

        // Make sure facts weren't clobbered:
        assertThat(consumer.getFacts()).hasSize(1);
    }

    @Test
    public void shouldNotAllowAnRhsmClientToRegisterAManifestConsumer() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        userClient.getApiClient().addDefaultHeader("user-agent", "RHSM/1.0 (cmd=subscription-manager)");
        ConsumerDTO consumer = Consumers.random(owner).type(ConsumerTypes.Candlepin.value());
        assertBadRequest(() -> userClient.consumers().createConsumer(consumer));
    }

    @Test
    public void shouldAllowAConsumerFactToBeRemovedWhenUpdatedBadly() {
        // typing for certain facts. violation means value for fact is entirely removed
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        // Set a single fact, so we can make sure it doesn't get clobbered:
        Map<String, String> facts = Map.of("system.machine", "x86_64",
            "lscpu.socket(s)", "4",
            "cpu.cpu(s)", "12");
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner).facts(facts));

        // Now update the facts, must send all:
        facts = Map.of("system.machine", "x86_64",
            "lscpu.socket(s)", "four",
            "cpu.cpu(s)", "8",
            // these facts don't need to be an int, they are ranges
            "lscpu.numa_node0_cpu(s)", "0-3",
            "lscpu.on-line_cpu(s)_list", "0-3");

        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().facts(facts));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());

        // Make sure facts weren't clobbered:
        assertThat(consumer.getFacts())
            .returns("x86_64", x -> x.get("system.machine"))
            .returns(null, x -> x.get("lscpu.socket(s)"))
            .returns("8", x -> x.get("cpu.cpu(s)"))
            // range facts should be left alone, rhbz #950462 shows
            // them being ignored
            .returns("0-3", x -> x.get("lscpu.numa_node0_cpu(s)"))
            .returns("0-3", x -> x.get("lscpu.on-line_cpu(s)_list"));
    }

    @Test
    public void shouldReturnCorrectExceptionForConstraintViolations() {
        final ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        assertBadRequest(() ->
            userClient.consumers().createConsumer(Consumers.random(owner).name("a".repeat(256))));
        assertBadRequest(() ->
            userClient.consumers().createConsumer(Consumers.random(owner), "a".repeat(256), owner.getKey(),
            null, true));
        assertBadRequest(() ->
            userClient.consumers().createConsumer(Consumers.random(owner).name(null).username(null)));
    }

    @Test
    public void shouldReturn404Or410WhenConcurrentRegisterIsDeletedByAnotherRequest() throws Exception {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));

        int totalThreads = 50;
        AtomicInteger threadCount = new AtomicInteger();
        List<Exception> unexpectedExceptions = new ArrayList<>();
        List<ApiException> expectedExceptions = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        IntStream.range(0, totalThreads).forEach(entry -> {
            Thread t = new Thread(() -> {
                try {
                    userClient.consumers().deleteConsumer(consumer.getUuid());
                }
                catch (ApiException e) {
                    if (e.getCode() == 410 || e.getCode() == 404) {
                        expectedExceptions.add(e);
                    }
                    else {
                        unexpectedExceptions.add(e);
                    }
                }
                catch (Exception e) {
                    unexpectedExceptions.add(e);
                }
                threadCount.getAndIncrement();
            });
            threads.add(t);
        });

        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }
        assertThat(threadCount.get()).isEqualTo(totalThreads);
        assertThat(unexpectedExceptions).isEmpty();
        assertThat(expectedExceptions).isNotEmpty();

        // Note: 404 can be returned in cases where a request was made after the initial deletion.
        //  With a large number of requests, we should expect 1 or more of each.
        assertThat(expectedExceptions)
            .filteredOn(x -> x.getCode() == 410 || x.getCode() == 404)
            .hasSize(expectedExceptions.size());
    }

    @Test
    public void shouldFetchConsumersWithFacts() {
        this.adminClient.consumers().createConsumer(Consumers.random(this.owner));

        ConsumerDTO target = Consumers.random(this.owner)
            .putFactsItem("fact1", "value1");

        target = this.adminClient.consumers().createConsumer(target);

        ConsumerDTO decoy = Consumers.random(this.owner)
            .putFactsItem("fact2", "value2");

        this.adminClient.consumers().createConsumer(decoy);

        List<String> facts = List.of("fact1:value1");

        List<ConsumerDTOArrayElement> output = this.adminClient.consumers()
            .searchConsumers(null, null, this.owner.getKey(), null, null, null, facts, null, null, null,
            null, null);

        assertThat(output)
            .isNotNull()
            .singleElement()
            .isNotNull()
            .returns(target.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldIgnoreDuplicateFacts() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        Map<String, String> facts = Map.of("system.machine", "x86_64",
            "System.machine", "x86_64",
            "System.Machine", "x86_64",
            "SYSTEM.MACHINE", "x86_64");
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner).facts(facts));
        assertThat(consumer.getFacts()).hasSize(1);
    }

    @Test
    public void shouldFilterConsumersByFactsAndNothingElse() {
        UserDTO user = UserUtil.createUser(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        String valueRand = StringUtil.random("");
        userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("key", valueRand, "otherkey", "other" + valueRand)));
        userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("key", valueRand, "otherkey", "some" + valueRand)));
        userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("newkey", "some" + valueRand)));
        assertThat(adminClient.consumers().searchConsumers(null, null, null, null,
            null, null, List.of("*key*:*" + valueRand + "*"), null, null, null, null, null))
            .hasSize(3);
    }

    @Test
    public void shouldFilterConsumersByFactsAndUuids() {
        UserDTO user = UserUtil.createUser(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        String valueRand = StringUtil.random("");
        ConsumerDTO consumer1 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("key", valueRand, "otherkey", "other" + valueRand)));
        ConsumerDTO consumer2 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("key", valueRand, "otherkey", "some" + valueRand)));
        ConsumerDTO consumer3 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("newkey", "some" + valueRand)));

        List<String> expectedUuids = List.of(consumer1.getUuid(), consumer2.getUuid());
        List<ConsumerDTOArrayElement> consumers = adminClient.consumers().searchConsumers(null, null, null,
            expectedUuids, null, null, List.of("oth*key*:*" + valueRand), null, null, null, null, null);
        assertThat(consumers).hasSize(2)
            .map(x -> x.getUuid())
            .containsAll(expectedUuids);
    }

    @Test
    public void shouldProperlyEscapeValuesToAvoidSqlInjection() {
        UserDTO user = UserUtil.createUser(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        String valueRand = StringUtil.random("");
        ConsumerDTO consumer1 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("trolol", valueRand + "); DROP TABLE cp_consumer;")));
        assertThat(adminClient.consumers().searchConsumers(null, null, null, null,
            null, null, List.of("trolol:" + valueRand + "); DROP TABLE cp_consumer;"), null, null, null,
            null, null))
            .singleElement()
            .returns(consumer1.getUuid(), x -> x.getUuid());
    }

    @Test
    public void shouldNotListConsumersWhenJustEnvironmentIdIsSpecified() {
        assertBadRequest(() -> adminClient.consumers().searchConsumers(null, null, null, null,
            null, null, null, "envId", null, null, null, null));
    }

    @Test
    public void shouldNotFindAnyConsumerWithUnknownEnvironmentId() {
        List<ConsumerDTOArrayElement> consumers = adminClient.consumers().searchConsumers(null, null,
            owner.getKey(), null, null, null, null, "envId", null, null, null, null);

        assertThat(consumers)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void shouldFindConsumersForGivenEnvironmentId() {
        EnvironmentDTO environment = adminClient.owners().createEnvironment(
            owner.getKey(), Environments.random());
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(
            Consumers.random(owner).environments(List.of(environment)));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(
            Consumers.random(owner).environments(List.of(environment)));

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers().searchConsumers(null, null,
            owner.getKey(), null, null, null, null, environment.getId(), null, null, null, null);

        assertThat(consumers)
            .isNotNull()
            .hasSize(2)
            .extracting(ConsumerDTOArrayElement::getId)
            .containsOnly(consumer1.getId(), consumer2.getId());
    }

    @Test
    public void shouldOnlyFindConsumerForGivenEnvironmentId() {
        EnvironmentDTO environment = adminClient.owners().createEnvironment(
            owner.getKey(), Environments.random());
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(
            Consumers.random(owner).environments(List.of(environment)));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers().searchConsumers(null, null,
            owner.getKey(), null, null, null, null, environment.getId(), null, null, null, null);

        assertThat(consumers)
            .isNotNull()
            .hasSize(1)
            .extracting(ConsumerDTOArrayElement::getId)
            .containsOnly(consumer1.getId())
            .doesNotContain(consumer2.getId());
    }

    @Test
    public void shouldOnlyFindConsumersForGivenEnvironmentIdAndOwner() {
        OwnerDTO anotherOwner = adminClient.owners().createOwner(Owners.random());
        EnvironmentDTO environment1 = adminClient.owners().createEnvironment(
            owner.getKey(), Environments.random());
        EnvironmentDTO environment2 = adminClient.owners().createEnvironment(
            anotherOwner.getKey(), Environments.random());
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(
            Consumers.random(owner).environments(List.of(environment1)));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(
            Consumers.random(owner).environments(List.of(environment1)));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(
            Consumers.random(anotherOwner).environments(List.of(environment2)));

        List<ConsumerDTOArrayElement> ownerConsumers = adminClient.consumers().searchConsumers(null, null,
            owner.getKey(), null, null, null, null, environment1.getId(), null, null, null, null);
        List<ConsumerDTOArrayElement> anotherOwnerConsumers =
            adminClient.consumers().searchConsumers(null, null, anotherOwner.getKey(), null, null, null, null,
                environment2.getId(), null, null, null, null);

        assertThat(ownerConsumers)
            .isNotNull()
            .hasSize(2)
            .extracting(ConsumerDTOArrayElement::getId)
            .containsOnly(consumer1.getId(), consumer2.getId())
            .doesNotContain(consumer3.getId());

        assertThat(anotherOwnerConsumers)
            .isNotNull()
            .hasSize(1)
            .extracting(ConsumerDTOArrayElement::getId)
            .containsOnly(consumer3.getId())
            .doesNotContain(consumer1.getId(), consumer2.getId());
    }

    @Test
    public void shouldProduceCheckInForCloudConsumer() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.randomAWS(owner));

        OffsetDateTime before = consumer.getLastCheckin();

        // Produce a check-in event
        ApiClients.ssl(consumer)
            .consumers()
            .getEntitlementCertificateSerials(consumer.getUuid());

        ConsumerDTO consumerAfterCheckIn = adminClient.consumers().getConsumer(consumer.getUuid());

        // Verify that the last check-in time has been updated
        assertThat(consumerAfterCheckIn)
            .isNotNull()
            .doesNotReturn(before, ConsumerDTO::getLastCheckin);
    }

    @Test
    public void shouldIndicateOwnerIsAnonymousWhenRetrievingConsumer() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO anonymousOwner = adminClient.owners().createOwner(Owners.randomSca()
            .anonymous(true));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(anonymousOwner));

        ConsumerDTO actual = adminClient.consumers().getConsumer(consumer.getUuid());

        assertThat(actual)
            .isNotNull()
            .extracting(ConsumerDTO::getOwner)
            .isNotNull()
            .returns(true, NestedOwnerDTO::getAnonymous);
    }

    @Test
    public void shouldIndicateOwnerIsNotAnonymousWhenRetrievingConsumer() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));

        ConsumerDTO actual = adminClient.consumers().getConsumer(consumer.getUuid());

        assertThat(actual)
            .isNotNull()
            .extracting(ConsumerDTO::getOwner)
            .isNotNull()
            .returns(false, NestedOwnerDTO::getAnonymous);
    }

    private UserDTO createUserTypeAllAccess(ApiClient client, OwnerDTO owner) {
        return UserUtil.createWith(client,
            Permissions.USERNAME_CONSUMERS.all(owner),
            Permissions.OWNER_POOLS.all(owner),
            Permissions.ATTACH.all(owner));
    }
}
