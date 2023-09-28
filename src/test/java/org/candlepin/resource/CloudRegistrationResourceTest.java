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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.CloudAccountOrgSetupJob.CloudAccountOrgSetupJobConfig;
import org.candlepin.auth.CloudAuthTokenGenerator;
import org.candlepin.auth.CloudAuthTokenType;
import org.candlepin.auth.CloudRegistrationData;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.api.server.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.server.v1.CloudRegistrationDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.exceptions.NotImplementedException;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.service.CloudProvider;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.model.CloudAuthenticationResult;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;

import javax.ws.rs.core.Response;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CloudRegistrationResourceTest {

    private Configuration mockConfig;
    private I18n i18n;
    private CloudRegistrationAdapter mockCloudRegistrationAdapter;
    private OwnerCurator mockOwnerCurator;
    private AnonymousCloudConsumerCurator mockAnonCloudConsumerCurator;
    private PoolCurator mockPoolCurator;
    private JobManager mockJobManager;
    private CloudAuthTokenGenerator mockTokenGenerator;

    private Principal principal;

    private CloudRegistrationResource cloudRegResource;

    @BeforeEach
    public void init() {
        this.mockConfig = mock(Configuration.class);
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.mockCloudRegistrationAdapter = mock(CloudRegistrationAdapter.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);
        this.mockAnonCloudConsumerCurator = mock(AnonymousCloudConsumerCurator.class);
        this.mockPoolCurator = mock(PoolCurator.class);
        this.mockJobManager = mock(JobManager.class);
        this.mockTokenGenerator = mock(CloudAuthTokenGenerator.class);

        doReturn(true).when(mockConfig).getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);
        this.principal = new UserPrincipal("test_user", null, false);
        ResteasyContext.pushContext(Principal.class, this.principal);

        cloudRegResource = new CloudRegistrationResource(this.mockConfig, this.i18n,
            this.mockCloudRegistrationAdapter, this.mockOwnerCurator,
            this.mockAnonCloudConsumerCurator, this.mockPoolCurator, this.mockJobManager,
            this.mockTokenGenerator);
    }

    @Test
    public void testCloudAuthorizeWithNullCloudRegistrationDTO() {
        assertThrows(BadRequestException.class, () -> cloudRegResource.cloudAuthorize(null, 1));
    }

    @Test
    public void testCloudAuthorizeWithCloudAuthenticationDisabled() {
        doReturn(false).when(mockConfig).getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);

        cloudRegResource = new CloudRegistrationResource(this.mockConfig, this.i18n,
            this.mockCloudRegistrationAdapter, this.mockOwnerCurator,
            this.mockAnonCloudConsumerCurator, this.mockPoolCurator, this.mockJobManager,
            this.mockTokenGenerator);

        assertThrows(NotImplementedException.class,
            () -> cloudRegResource.cloudAuthorize(new CloudRegistrationDTO(), 1));
    }

    @Test
    public void testCloudAuthorizeUsingV1Auth() {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String expectedOwnerKey = "owner-key";
        String expectedToken = "token";
        doReturn(expectedOwnerKey).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationData(getCloudRegistrationData(dto));
        doReturn(expectedToken).when(mockTokenGenerator).buildStandardRegistrationToken(principal,
            expectedOwnerKey);

        Response response = cloudRegResource.cloudAuthorize(dto, 1);

        assertThat(response)
            .isNotNull()
            .returns(200, Response::getStatus)
            .returns(expectedToken, Response::getEntity);
    }

    @Test
    public void testCloudAuthorizeWithUnableToResolveOwnerKeyUsingV1Auth() {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 1));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testCloudAuthorizeWithInvalidCloudAccountIdUsingV2Auth(String cloudAccountId)
        throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult(cloudAccountId, "instanceId",
            CloudProvider.AWS,
            "ownerKey", "offerId", "productId", true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testCloudAuthorizeWithInvalidCloudInstanceIdUsingV2Auth(String instanceId)
        throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult("cloudAccountId", instanceId,
            CloudProvider.AWS,
            "ownerKey", "offerId", "productId", true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @Test
    public void testCloudAuthorizeWithNullCloudProviderUsingV2Auth() {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult("cloudAccountId", "instanceId", null,
            "ownerKey", "offerId", "productId", true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testCloudAuthorizeWithInvalidOfferIdUsingV2Auth(String offerId)
        throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult("cloudAccountId", "instanceId",
            CloudProvider.AWS,
            "ownerKey", offerId, "productId", true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testCloudAuthorizeWithInvalidProductIdUsingV2Auth(String productId) throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult("cloudAccountId", "instanceId",
            CloudProvider.AWS,
            "ownerKey", "offerId", productId, true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @Test
    public void testCloudAuthorizeWithEntitledOwnerThatsSyncedInCandlepinUsingV2Auth() throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String expectedOwnerKey = "ownerKey";
        String prodId = "productId";
        CloudAuthenticationResult result = buildMockAuthResult("cloudAccountId", "instanceId",
            CloudProvider.AWS,
            expectedOwnerKey, "offerId", prodId, true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));
        Owner owner = new Owner().setKey(expectedOwnerKey);
        doReturn(owner).when(mockOwnerCurator).getByKey(expectedOwnerKey);
        doReturn(true).when(mockPoolCurator).hasPoolForProduct(owner.getKey(), prodId);

        String expectedToken = "standard-token";
        doReturn(expectedToken).when(mockTokenGenerator).buildStandardRegistrationToken(principal,
            expectedOwnerKey);

        Response response = cloudRegResource.cloudAuthorize(dto, 2);

        assertThat(response)
            .isNotNull()
            .returns(200, Response::getStatus)
            .extracting(Response::getEntity)
            .isNotNull()
            .isInstanceOf(CloudAuthenticationResultDTO.class);

        assertThat((CloudAuthenticationResultDTO) response.getEntity())
            .returns(expectedOwnerKey, CloudAuthenticationResultDTO::getOwnerKey)
            .returns(null, CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(expectedToken, CloudAuthenticationResultDTO::getToken)
            .returns(CloudAuthTokenType.STANDARD.toString(), CloudAuthenticationResultDTO::getTokenType);

        verify(mockJobManager, never()).queueJob(any(CloudAccountOrgSetupJobConfig.class));
    }

    @Test
    public void testCloudAuthorizeWithNoExistingOwnerFromAdapterUsingV2Auth() throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String prodId = "productId";
        String accountId = "cloudAccountId";
        String offeringId = "offerId";
        CloudAuthenticationResult result = buildMockAuthResult(accountId, "instanceId", CloudProvider.AWS,
            null, offeringId, prodId, true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId("instanceId")
            .setCloudProviderShortName(CloudProvider.AWS.shortName())
            .setProductId(prodId);
        doReturn(anonConsumer).when(mockAnonCloudConsumerCurator).create(any(AnonymousCloudConsumer.class));

        String expectedToken = "anon-token";
        doReturn(expectedToken).when(mockTokenGenerator).buildAnonymousRegistrationToken(principal,
            anonConsumer.getUuid());

        Response response = cloudRegResource.cloudAuthorize(dto, 2);

        assertThat(response)
            .isNotNull()
            .returns(200, Response::getStatus)
            .extracting(Response::getEntity)
            .isNotNull()
            .isInstanceOf(CloudAuthenticationResultDTO.class);

        assertThat((CloudAuthenticationResultDTO) response.getEntity())
            .returns(null, CloudAuthenticationResultDTO::getOwnerKey)
            .returns(anonConsumer.getUuid(), CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(expectedToken, CloudAuthenticationResultDTO::getToken)
            .returns(CloudAuthTokenType.ANONYMOUS.toString(), CloudAuthenticationResultDTO::getTokenType);

        verify(mockJobManager).queueJob(any(CloudAccountOrgSetupJobConfig.class));
    }

    @Test
    public void testCloudAuthorizeWithUnEntitledOwnerFromAdapterUsingV2Auth() throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String expectedOwnerKey = "ownerKey";
        String prodId = "productId";
        String accountId = "cloudAccountId";
        CloudAuthenticationResult result = buildMockAuthResult(accountId, "instanceId", CloudProvider.AWS,
            expectedOwnerKey, "offerId", prodId, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));
        Owner owner = new Owner().setKey(expectedOwnerKey);
        doReturn(owner).when(mockOwnerCurator).getByKey(expectedOwnerKey);
        doReturn(true).when(mockPoolCurator).hasPoolForProduct(owner.getKey(), prodId);

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId("instanceId")
            .setCloudProviderShortName(CloudProvider.AWS.shortName())
            .setProductId(prodId);
        doReturn(anonConsumer).when(mockAnonCloudConsumerCurator).create(any(AnonymousCloudConsumer.class));

        String expectedToken = "anon-token";
        doReturn(expectedToken).when(mockTokenGenerator).buildAnonymousRegistrationToken(principal,
            anonConsumer.getUuid());

        Response response = cloudRegResource.cloudAuthorize(dto, 2);

        assertThat(response)
            .isNotNull()
            .returns(200, Response::getStatus)
            .extracting(Response::getEntity)
            .isNotNull()
            .isInstanceOf(CloudAuthenticationResultDTO.class);

        assertThat((CloudAuthenticationResultDTO) response.getEntity())
            .returns(expectedOwnerKey, CloudAuthenticationResultDTO::getOwnerKey)
            .returns(anonConsumer.getUuid(), CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(expectedToken, CloudAuthenticationResultDTO::getToken)
            .returns(CloudAuthTokenType.ANONYMOUS.toString(), CloudAuthenticationResultDTO::getTokenType);

        verify(mockJobManager).queueJob(any(CloudAccountOrgSetupJobConfig.class));
    }

    @Test
    public void testCloudAuthorizeWithNoExistingOwnerInCandlepinUsingV2Auth() throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String expectedOwnerKey = "ownerKey";
        String prodId = "productId";
        String accountId = "cloudAccountId";
        CloudAuthenticationResult result = buildMockAuthResult(accountId, "instanceId", CloudProvider.AWS,
            expectedOwnerKey, "offerId", prodId, true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId("instanceId")
            .setCloudProviderShortName(CloudProvider.AWS.shortName())
            .setProductId(prodId);
        doReturn(anonConsumer).when(mockAnonCloudConsumerCurator).create(any(AnonymousCloudConsumer.class));

        String expectedToken = "anon-token";
        doReturn(expectedToken).when(mockTokenGenerator).buildAnonymousRegistrationToken(principal,
            anonConsumer.getUuid());

        Response response = cloudRegResource.cloudAuthorize(dto, 2);

        assertThat(response)
            .isNotNull()
            .returns(200, Response::getStatus)
            .extracting(Response::getEntity)
            .isNotNull()
            .isInstanceOf(CloudAuthenticationResultDTO.class);

        assertThat((CloudAuthenticationResultDTO) response.getEntity())
            .returns(expectedOwnerKey, CloudAuthenticationResultDTO::getOwnerKey)
            .returns(anonConsumer.getUuid(), CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(expectedToken, CloudAuthenticationResultDTO::getToken)
            .returns(CloudAuthTokenType.ANONYMOUS.toString(), CloudAuthenticationResultDTO::getTokenType);

        verify(mockJobManager, never()).queueJob(any(CloudAccountOrgSetupJobConfig.class));
    }

    @Test
    public void testCloudAuthorizeWithNoExistingPoolUsingV2Auth() throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String expectedOwnerKey = "ownerKey";
        String prodId = "productId";
        String accountId = "cloudAccountId";
        CloudAuthenticationResult result = buildMockAuthResult(accountId, "instanceId", CloudProvider.AWS,
            expectedOwnerKey, "offerId", prodId, true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));
        Owner owner = new Owner().setKey(expectedOwnerKey);
        doReturn(owner).when(mockOwnerCurator).getByKey(expectedOwnerKey);
        doReturn(false).when(mockPoolCurator).hasPoolForProduct(owner.getKey(), prodId);

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId("instanceId")
            .setCloudProviderShortName(CloudProvider.AWS.shortName())
            .setProductId(prodId);
        doReturn(anonConsumer).when(mockAnonCloudConsumerCurator).create(any(AnonymousCloudConsumer.class));

        String expectedToken = "anon-token";
        doReturn(expectedToken).when(mockTokenGenerator).buildAnonymousRegistrationToken(principal,
            anonConsumer.getUuid());

        Response response = cloudRegResource.cloudAuthorize(dto, 2);

        assertThat(response)
            .isNotNull()
            .returns(200, Response::getStatus)
            .extracting(Response::getEntity)
            .isNotNull()
            .isInstanceOf(CloudAuthenticationResultDTO.class);

        assertThat((CloudAuthenticationResultDTO) response.getEntity())
            .returns(expectedOwnerKey, CloudAuthenticationResultDTO::getOwnerKey)
            .returns(anonConsumer.getUuid(), CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(expectedToken, CloudAuthenticationResultDTO::getToken)
            .returns(CloudAuthTokenType.ANONYMOUS.toString(), CloudAuthenticationResultDTO::getTokenType);

        verify(mockJobManager, never()).queueJob(any(CloudAccountOrgSetupJobConfig.class));
    }

    @Test
    public void testCloudAuthorizeWithNoExistingPoolAndExistingAnonConsumerUsingV2Auth() throws Exception {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String expectedOwnerKey = "ownerKey";
        String prodId = "productId";
        String accountId = "cloudAccountId";
        String instanceId = "instanceId";
        CloudAuthenticationResult result = buildMockAuthResult(accountId, instanceId, CloudProvider.AWS,
            expectedOwnerKey, "offerId", prodId, true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));
        Owner owner = new Owner().setKey(expectedOwnerKey);
        doReturn(owner).when(mockOwnerCurator).getByKey(expectedOwnerKey);
        doReturn(false).when(mockPoolCurator).hasPoolForProduct(owner.getKey(), prodId);

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId(instanceId)
            .setCloudProviderShortName(CloudProvider.AWS.shortName())
            .setProductId(prodId);
        doReturn(anonConsumer).when(mockAnonCloudConsumerCurator).getByCloudInstanceId(instanceId);

        String expectedToken = "anon-token";
        doReturn(expectedToken).when(mockTokenGenerator).buildAnonymousRegistrationToken(principal,
            anonConsumer.getUuid());

        Response response = cloudRegResource.cloudAuthorize(dto, 2);

        assertThat(response)
            .isNotNull()
            .returns(200, Response::getStatus)
            .extracting(Response::getEntity)
            .isNotNull()
            .isInstanceOf(CloudAuthenticationResultDTO.class);

        assertThat((CloudAuthenticationResultDTO) response.getEntity())
            .returns(expectedOwnerKey, CloudAuthenticationResultDTO::getOwnerKey)
            .returns(anonConsumer.getUuid(), CloudAuthenticationResultDTO::getAnonymousConsumerUuid)
            .returns(expectedToken, CloudAuthenticationResultDTO::getToken)
            .returns(CloudAuthTokenType.ANONYMOUS.toString(), CloudAuthenticationResultDTO::getTokenType);

        verify(mockJobManager, never()).queueJob(any(CloudAccountOrgSetupJobConfig.class));
    }

    @Test
    public void testAuthorizeWithUnknownVersion() {
        assertThrows(BadRequestException.class,
            () -> cloudRegResource.cloudAuthorize(new CloudRegistrationDTO(), 100));
    }

    private CloudRegistrationData getCloudRegistrationData(CloudRegistrationDTO cloudRegistrationDTO) {
        CloudRegistrationData registrationData = new CloudRegistrationData();
        registrationData.setType(cloudRegistrationDTO.getType());
        registrationData.setMetadata(cloudRegistrationDTO.getMetadata());
        registrationData.setSignature(cloudRegistrationDTO.getSignature());
        return registrationData;
    }

    private CloudAuthenticationResult buildMockAuthResult(String cloudAccountId, String instanceId,
        CloudProvider provider,
        String ownerKey, String offerId, String productId, boolean isEntitled) {
        CloudAuthenticationResult mockResult = mock(CloudAuthenticationResult.class);
        doReturn(cloudAccountId).when(mockResult).getCloudAccountId();
        doReturn(instanceId).when(mockResult).getCloudInstanceId();
        doReturn(provider).when(mockResult).getCloudProvider();
        doReturn(ownerKey).when(mockResult).getOwnerKey();
        doReturn(offerId).when(mockResult).getOfferId();
        doReturn(productId).when(mockResult).getProductId();
        doReturn(isEntitled).when(mockResult).isEntitled();

        return mockResult;
    }

}
