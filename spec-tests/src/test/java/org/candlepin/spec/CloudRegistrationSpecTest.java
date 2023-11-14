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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotImplemented;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertThatStatus;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnauthorized;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
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
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Base64;
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
        String token = cloudRegistration.cloudAuthorize(owner.getKey(), "test-type", "test_signature");

        assertTokenType(adminClient.MAPPER, token, STANDARD_TOKEN_TYPE);
        assertNotNull(token);
    }

    @Test
    public void shouldAllowRegistrationWithValidToken() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        HostedTestApi upstreamClient = adminClient.hosted();
        CloudRegistrationClient cloudRegistration = adminClient.cloudAuthorization();
        OwnerDTO owner = upstreamClient.createOwner(Owners.random());
        String token = cloudRegistration.cloudAuthorize(owner.getKey(), "test-type", "test_signature");
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
        assertBadRequest(() -> cloudRegistration.cloudAuthorize(owner, type, signature));
    }

    @Test
    public void shouldAllowRegistrationWithEmptySignature() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        HostedTestApi upstreamClient = adminClient.hosted();
        CloudRegistrationClient cloudRegistration = adminClient.cloudAuthorization();
        OwnerDTO owner = upstreamClient.createOwner(Owners.random());
        String token = cloudRegistration.cloudAuthorize(owner.getKey(), "test-type", "");
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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(prod.getId()));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(prod.getId()));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(StringUtil.random("prod-")));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(StringUtil.random("prod-")));

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(prod.getId()));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(prod.getId()));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(StringUtil.random("prod-")));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");
        String expectedAnonConsumerUuid = result.getAnonymousConsumerUuid();

        CloudAuthenticationResultDTO actual = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(prod.getId()));

        assertBadRequest(() -> adminClient.cloudAuthorization()
            .cloudAuthorizeV2(null, instanceId, offerId, "test-type", ""));
    }

    @Test
    public void shouldReturnBadRequestWithMissingCloudInstanceIdForV2Auth() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.hosted().createOwner(Owners.random());
        ProductDTO prod = adminClient.hosted().createProduct(Products.random());
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        String cloudAccountId = StringUtil.random("cloud-account-id-");
        String offerId = StringUtil.random("cloud-offer-");

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(prod.getId()));

        assertBadRequest(() -> adminClient.cloudAuthorization()
            .cloudAuthorizeV2(cloudAccountId, null, offerId, "test-type", ""));
    }

    @Test
    public void shouldReturnBadRequestWithMissingCloudOfferingIdForV2Auth() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.hosted().createOwner(Owners.random());
        ProductDTO prod = adminClient.hosted().createProduct(Products.random());
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        String cloudAccountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");

        adminClient.hosted().associateOwnerToCloudAccount(cloudAccountId, owner.getKey());

        assertBadRequest(() -> adminClient.cloudAuthorization()
            .cloudAuthorizeV2(cloudAccountId, instanceId, null, "test-type", ""));
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

        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        assertUnauthorized(() -> adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", ""));
    }

    @Test
    public void shouldThrowNotImplementedExceptionWith1POfferingForV2Auth()
        throws Exception {
        ApiClient adminClient = ApiClients.admin();
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        ProductDTO product = adminClient.hosted().createProduct(Products.random());
        adminClient.hosted().associateProductIdsToCloudOffer(offerId, "1P", List.of(product.getId()));

        assertNotImplemented(() -> adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", ""));
    }

    @Test
    public void shouldExportCertificatesWithAnonymousToken() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        ProductDTO product = adminClient.hosted().createProduct(Products.random());
        ContentDTO content1 = adminClient.hosted().createContent(Contents.random());
        ContentDTO content2 = adminClient.hosted().createContent(Contents.random());

        adminClient.hosted().addContentToProduct(product.getId(), content1.getId(), true);
        adminClient.hosted().addContentToProduct(product.getId(), content2.getId(), true);
        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(product.getId()));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

        assertTokenType(adminClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .doesNotReturn(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(ANON_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);

        List<JsonNode> certs = ApiClients.bearerToken(result.getToken()).consumers()
            .exportCertificates(result.getAnonymousConsumerUuid(), null);

        assertThat(certs).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds.get("content_access"))
            .isNotNull()
            .hasSize(2)
            .contains(content1.getId(), content2.getId());
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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(StringUtil.random("prod-")));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

        assertTokenType(adminClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);
        assertThat(result)
            .isNotNull()
            .returns(owner.getKey(), CloudAuthenticationResultDTO::getOwnerKey)
            .doesNotReturn(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(ANON_TOKEN_TYPE, CloudAuthenticationResultDTO::getTokenType);

        assertNotFound(() -> ApiClients.bearerToken(result.getToken()).consumers()
            .exportCertificates(StringUtil.random("unknown-"), null));
    }

    @Test
    public void shouldCreateConsumerWhenUsingStandardToken() throws JsonProcessingException {
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

        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(prod.getId()));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

        assertTokenType(ApiClient.MAPPER, result.getToken(), STANDARD_TOKEN_TYPE);

        ConsumerDTO consumer = ApiClients.bearerToken(result.getToken()).consumers()
            .createConsumer(Consumers.random(owner));

        assertNotNull(consumer);
    }

    @Test
    public void shouldVerifyAnonymousTokenRegistration() throws JsonProcessingException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO ownerDTO = Owners.random();
        ProductDTO productDTO = Products.random();

        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        adminClient.hosted().createProduct(productDTO);
        adminClient.hosted().associateProductIdsToCloudOffer(offerId, List.of(productDTO.getId()));

        CloudAuthenticationResultDTO result = adminClient.cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

        assertTokenType(ApiClient.MAPPER, result.getToken(), ANON_TOKEN_TYPE);

        // The Org does not exist at all in the backend services upstream of Candlepin yet
        assertThatStatus(() -> ApiClients.bearerToken(result.getToken()).consumers()
            .createConsumer(Consumers.random(ownerDTO)))
            .isTooMany()
            .hasHeaderWithValue("retry-after", 300);

        adminClient.hosted().createOwner(ownerDTO);
        adminClient.hosted().associateOwnerToCloudAccount(accountId, ownerDTO.getKey());

        // The Org exists upstream of Candlepin, but does not have SKU(s) subscribed to it yet
        assertThatStatus(() -> ApiClients.bearerToken(result.getToken()).consumers()
            .createConsumer(Consumers.random(ownerDTO)))
            .isTooMany()
            .hasHeaderWithValue("retry-after", 180);

        adminClient.hosted().createSubscription(Subscriptions.random(ownerDTO, productDTO));

        // No product and no owner exist in Candlepin
        assertThatStatus(() -> ApiClients.bearerToken(result.getToken()).consumers()
            .createConsumer(Consumers.random(ownerDTO)))
            .isTooMany()
            .hasHeaderWithValue("retry-after", 60);


        // Just owner exist in Candlepin
        adminClient.owners().createOwner(ownerDTO);
        assertThatStatus(() -> ApiClients.bearerToken(result.getToken()).consumers()
            .createConsumer(Consumers.random(ownerDTO)))
            .isTooMany()
            .hasHeaderWithValue("retry-after", 30);

        // Product and owner exist in Candlepin
        adminClient.ownerProducts().createProductByOwner(ownerDTO.getKey(), productDTO);
        adminClient.owners().createPool(ownerDTO.getKey(), Pools.random(productDTO));
        ConsumerDTO consumer = ApiClients.bearerToken(result.getToken()).consumers()
            .createConsumer(Consumers.random(ownerDTO));

        assertThat(consumer)
            .isNotNull()
            .extracting(ConsumerDTO::getIdCert)
            .isNotNull();
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

    private static Stream<Arguments> tokenVariation() throws ApiException {
        OwnerDTO owner = ApiClients.admin().hosted().createOwner(Owners.random());

        return Stream.of(
            Arguments.of(null, null, null),
            Arguments.of(null, "test-type", "test_signature"),
            Arguments.of(owner.getKey(), null, "test_signature"),
            Arguments.of(owner.getKey(), "test-type", null));
    }
}
