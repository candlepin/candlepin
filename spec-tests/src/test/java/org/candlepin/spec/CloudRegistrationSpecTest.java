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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnauthorized;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.client.v1.CloudRegistrationDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyWithCapability;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.CloudRegistrationClient;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;



@SpecTest
@OnlyInHosted
@OnlyWithCapability("cloud_registration")
class CloudRegistrationSpecTest {

    private static final String STANDARD_TOKEN_TYPE = "CP-Cloud-Registration";
    private static final String ANON_TOKEN_TYPE = "CP-Anonymous-Cloud-Registration";

    @Test
    public void shouldGenerateValidTokenWithValidMetadata() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        HostedTestApi upstreamClient = adminClient.hosted();
        CloudRegistrationClient cloudRegistration = adminClient.cloudAuthorization();
        OwnerDTO owner = upstreamClient.createOwner(Owners.random());
        String token = cloudRegistration.cloudAuthorize(generateToken(owner.getKey(),
            "test-type", "test_signature"));

        assertTokenType(adminClient.MAPPER, token, STANDARD_TOKEN_TYPE);
        assertNotNull(token);
    }

    @Test
    public void shouldAllowRegistrationWithValidToken() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        HostedTestApi upstreamClient = adminClient.hosted();
        CloudRegistrationClient cloudRegistration = adminClient.cloudAuthorization();
        OwnerDTO owner = upstreamClient.createOwner(Owners.random());
        String token = cloudRegistration.cloudAuthorize(generateToken(owner.getKey(), "test-type",
            "test_signature"));
        ConsumerDTO consumer = ApiClients.bearerToken(token).consumers()
            .createConsumer(Consumers.random(owner));

        assertTokenType(adminClient.MAPPER, token, STANDARD_TOKEN_TYPE);
        assertNotNull(consumer);
    }

    @ParameterizedTest
    @MethodSource("tokenVariation")
    public void shouldFailCloudRegistration(String owner, String type, String signature) {
        ApiClient adminClient = ApiClients.admin();
        CloudRegistrationClient cloudRegistration = adminClient.cloudAuthorization();
        assertBadRequest(() -> cloudRegistration.cloudAuthorize(generateToken(owner, type, signature)));
    }

    @Test
    public void shouldAllowRegistrationWithEmptySignature() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        HostedTestApi upstreamClient = adminClient.hosted();
        CloudRegistrationClient cloudRegistration = adminClient.cloudAuthorization();
        OwnerDTO owner = upstreamClient.createOwner(Owners.random());
        String token = cloudRegistration.cloudAuthorize(generateToken(owner.getKey(),
            "test-type", ""));
        ConsumerDTO consumer = ApiClients.bearerToken(token).consumers()
            .createConsumer(Consumers.random(owner));
        assertNotNull(consumer);
        assertTokenType(adminClient.MAPPER, token, STANDARD_TOKEN_TYPE);
    }

    @Test
    public void shouldAllowV2AuthWithExistingEntitledOwnerForCloudAccountId() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(owner.getKey(), Products.random());
        adminClient.hosted().createProduct(prod);
        adminClient.owners().createPool(owner.getKey(), Pools.random(prod));
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));

        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(prod.getId()));
        associateOwnerToCloudAccount(adminClient, accountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));

        assertTokenType(adminClient.MAPPER, result.getToken(), STANDARD_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .returns(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(STANDARD_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);
    }

    @Test
    public void shouldAllowV2AuthWithExistingEntitledAnonymousOwnerForCloudAccountId() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random().anonymous(true));
        adminClient.hosted().createOwner(owner);
        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(owner.getKey(), Products.random());
        adminClient.hosted().createProduct(prod);
        adminClient.owners().createPool(owner.getKey(), Pools.random(prod));
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(prod.getId()));
        associateOwnerToCloudAccount(adminClient, accountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));

        assertTokenType(adminClient.MAPPER, result.getToken(), STANDARD_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .returns(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(STANDARD_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);
    }

    @Test
    public void shouldReceiveAnonTokenForV2AuthWithExistingOwnerForCloudAccountIdAndNoEntitlement()
        throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(StringUtil.random("prod-")));
        associateOwnerToCloudAccount(adminClient, accountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));

        assertTokenType(adminClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .doesNotReturn(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(ANON_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);
    }

    @Test
    public void shouldReceiveAnonTokenForV2AuthWithNoOwnerForCloudAccountId() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(StringUtil.random("prod-")));

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));

        assertTokenType(adminClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(null, CloudAuthenticationResultDTO::getOwnerKey)
            .doesNotReturn(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(ANON_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);
    }

    @Test
    public void shouldReceiveAnonTokenForV2AuthWithNonSyncedOwnerInCandlepin() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.hosted().createOwner(Owners.random());
        ProductDTO prod = adminClient.hosted().createProduct(Products.random());
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));

        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(prod.getId()));
        associateOwnerToCloudAccount(adminClient, accountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));

        assertTokenType(adminClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .doesNotReturn(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(ANON_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);
    }

    @Test
    public void shouldReceiveAnonTokenForV2AuthWithNonSyncedPoolInCandlepin() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(owner.getKey(), Products.random());
        adminClient.hosted().createProduct(prod);
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));

        ProductDTO prodWithPool = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());
        adminClient.hosted().createProduct(prodWithPool);
        adminClient.owners().createPool(owner.getKey(), Pools.random(prodWithPool));
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prodWithPool));

        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(prod.getId(), prodWithPool.getId()));
        associateOwnerToCloudAccount(adminClient, accountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));

        assertTokenType(adminClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .doesNotReturn(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(ANON_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);
    }

    @Test
    public void shouldReceiveSameAnonConsumerUuidWhenReAuthenticatingForV2Auth() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(StringUtil.random("prod-")));
        associateOwnerToCloudAccount(adminClient, accountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);
        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));
        String expectedAnonConsumerUuid = result.getAnonymousConsumerUuid();

        CloudAuthenticationResultDTO actual = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));

        assertTokenType(adminClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);
        assertThat(actual)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .returns(expectedAnonConsumerUuid, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(ANON_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);
    }

    @Test
    public void shouldReturnBadRequestWithMissingCloudAccountIdForV2Auth() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.hosted().createOwner(Owners.random());
        ProductDTO prod = adminClient.hosted().createProduct(Products.random());
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(prod.getId()));

        String metadata = buildMetadataJson(adminClient.MAPPER, null, instanceId, offerId);

        assertBadRequest(() -> adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", "")));
    }

    @Test
    public void shouldReturnBadRequestWithMissingCloudInstanceIdForV2Auth() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.hosted().createOwner(Owners.random());
        ProductDTO prod = adminClient.hosted().createProduct(Products.random());
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        String cloudAccountId = StringUtil.random("cloud-account-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(prod.getId()));

        String metadata = buildMetadataJson(adminClient.MAPPER, cloudAccountId, null, offerId);

        assertBadRequest(() -> adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", "")));
    }

    @Test
    public void shouldReturnBadRequestWithMissingCloudOfferingIdForV2Auth() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.hosted().createOwner(Owners.random());
        ProductDTO prod = adminClient.hosted().createProduct(Products.random());
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        String cloudAccountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");

        associateOwnerToCloudAccount(adminClient, cloudAccountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, cloudAccountId, instanceId, null);

        assertBadRequest(() -> adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", "")));
    }

    @Test
    public void shouldThrowNotAuthorizedExceptionWithUnknownProductIdForCloudOfferingForV2Auth()
        throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(owner.getKey(), Products.random());
        adminClient.hosted().createProduct(prod);
        adminClient.owners().createPool(owner.getKey(), Pools.random(prod));
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateOwnerToCloudAccount(adminClient, accountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);

        assertUnauthorized(() -> adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", "")));
    }

    @Test
    public void shouldExportCertificatesWithAnonymousToken() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(StringUtil.random("prod-")));
        associateOwnerToCloudAccount(adminClient, accountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
                .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));

        assertTokenType(adminClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .doesNotReturn(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(ANON_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);

        // TODO: As part of CANDLEPIN-626 this test should be updated to return a content access certificate.
        // As for now, this test verifies that the failure does not occur in the VerifyAuthorizationFilter,
        // but inside of the ConsumerResource.exportCertificates method. Asserting that the annonymous
        // bearer token has allowed access to the endpoint.
        assertNotFound(() -> ApiClients.bearerToken(result.getToken()).consumers()
            .exportCertificates(result.getAnonymousConsumerUuid(), null))
            .hasMessageContaining("Unit with ID", result.getAnonymousConsumerUuid(),
                "could not be found.");
    }

    @Test
    public void shouldNotExportCertificatesWithAnonymousTokenForUnknownAnonCloudConsumer()
            throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        associateProductIdToCloudOffer(adminClient, offerId, List.of(StringUtil.random("prod-")));
        associateOwnerToCloudAccount(adminClient, accountId, owner.getKey());

        String metadata = buildMetadataJson(adminClient.MAPPER, accountId, instanceId, offerId);

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(generateToken(metadata, "test-type", ""));

        assertTokenType(adminClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .doesNotReturn(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(ANON_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);

        assertNotFound(() -> ApiClients.bearerToken(result.getToken()).consumers()
            .exportCertificates(StringUtil.random("unknown"), null));
    }

    /**
     * Associates a cloud offering ID to product IDs in the hosted test adapters.
     *
     * @param client
     *     client used to make the request to the hosted test endpoint
     *
     * @param cloudOfferId
     *     the offering ID to associate to a product ID
     *
     * @param productIds
     *     the product IDs to associate to an offering ID
     */
    private void associateProductIdToCloudOffer(ApiClient client, String cloudOfferId,
        Collection<String> productIds) {
        ObjectNode objectNode = ApiClient.MAPPER.createObjectNode();
        objectNode.put("cloudOfferId", cloudOfferId);

        ArrayNode productsNode = ApiClient.MAPPER.createArrayNode();
        productIds.forEach(prod -> productsNode.add(prod));
        objectNode.putPOJO("productIds", productsNode);

        Response response = Request.from(client)
            .setPath("/hostedtest/cloud/offers")
            .setMethod("POST")
            .setBody(objectNode.toString())
            .execute();

        if (response.getCode() != 204) {
            throw new RuntimeException("unable to associate product to cloud offer");
        }
    }

    /**
     * Associates a cloud account ID to an owner ID in the hosted test adapters.
     *
     * @param client
     *     client used to make the request to the hosted test endpoint
     *
     * @param cloudAccountId
     *     the cloud account ID to associate to an owner ID
     *
     * @param ownerId
     *     the owner ID to associate to a cloud account ID
     */
    private void associateOwnerToCloudAccount(ApiClient client, String cloudAccountId, String ownerId) {
        ObjectNode objectNode = ApiClient.MAPPER.createObjectNode();
        objectNode.put("cloudAccountId", cloudAccountId);
        objectNode.put("ownerId", ownerId);

        Response response = Request.from(client)
            .setPath("/hostedtest/cloud/accounts")
            .setMethod("POST")
            .setBody(objectNode.toString())
            .execute();

        if (response.getCode() != 204) {
            throw new RuntimeException("unable to associate cloud account to owner");
        }
    }

    private String buildMetadataJson(ObjectMapper mapper, String accountId, String instanceId,
        String offeringId) {
        ObjectNode objectNode = mapper.createObjectNode();
        if (accountId != null) {
            objectNode.put("accountId", accountId);
        }

        if (instanceId != null) {
            objectNode.put("instanceId", instanceId);
        }

        if (offeringId != null) {
            objectNode.put("cloudOfferingId", offeringId);
        }

        return objectNode.toString();
    }

    private void assertTokenType(ObjectMapper mapper, String token, String expectedTokenType)
        throws JsonMappingException, JsonProcessingException {
        if (token == null || expectedTokenType == null) {
            throw new IllegalArgumentException("token or token type is null");
        }

        String[] chunks = token.split("\\.");
        if (chunks.length < 2) {
            throw new RuntimeException("unable to read token body");
        }

        Base64.Decoder decoder = Base64.getUrlDecoder();
        String body = new String(decoder.decode(chunks[1]));
        Map<String, String> bodyMap = mapper.readValue(body, HashMap.class);

        assertEquals(expectedTokenType, bodyMap.get("typ"));
    }

    private CloudRegistrationDTO generateToken(String metadata, String type, String signature) {
        return new CloudRegistrationDTO()
            .type(type)
            .metadata(metadata)
            .signature(signature);
    }

    private static Stream<Arguments> tokenVariation() throws ApiException {
        OwnerDTO owner = ApiClients.admin().hosted().createOwner(Owners.random());

        return Stream.of(
            Arguments.of(null, null, null),
            Arguments.of(null, "test-type", "test_signature"),
            Arguments.of(owner.getKey(), null, "test_signature"),
            Arguments.of(owner.getKey(), "test-type", null));
    }
}
