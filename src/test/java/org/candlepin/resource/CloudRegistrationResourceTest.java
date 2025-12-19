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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobArguments;
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
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificate;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.cloudregistration.CloudRegistrationNotSupportedForOfferingException;
import org.candlepin.service.model.CloudAuthenticationResult;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;


import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CloudRegistrationResourceTest {

    private Configuration mockConfig;
    private I18n i18n;
    private CloudRegistrationAdapter mockCloudRegistrationAdapter;
    private OwnerCurator mockOwnerCurator;
    private AnonymousCloudConsumerCurator mockAnonCloudConsumerCurator;
    private AnonymousContentAccessCertificateCurator mockAnonCloudCertCurator;
    private PoolCurator mockPoolCurator;
    private JobManager mockJobManager;
    private AsyncJobStatusCurator mockJobStatusCurator;
    private CloudAuthTokenGenerator mockTokenGenerator;
    private PrincipalProvider principalProvider;
    private Principal principal;

    private CloudRegistrationResource cloudRegResource;

    @BeforeEach
    public void init() {
        this.mockConfig = mock(Configuration.class);
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.mockCloudRegistrationAdapter = mock(CloudRegistrationAdapter.class);
        this.mockAnonCloudConsumerCurator = mock(AnonymousCloudConsumerCurator.class);
        this.mockPoolCurator = mock(PoolCurator.class);
        this.mockJobManager = mock(JobManager.class);
        this.mockJobStatusCurator = mock(AsyncJobStatusCurator.class);
        this.mockTokenGenerator = mock(CloudAuthTokenGenerator.class);
        this.mockTokenGenerator = mock(CloudAuthTokenGenerator.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);
        this.mockAnonCloudCertCurator = mock(AnonymousContentAccessCertificateCurator.class);
        this.principalProvider = mock(PrincipalProvider.class);

        doReturn(true).when(mockConfig).getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);
        this.principal = new UserPrincipal("test_user", null, false);
        doReturn(this.principal).when(principalProvider).get();

        cloudRegResource = new CloudRegistrationResource(this.mockConfig, this.i18n,
            this.mockCloudRegistrationAdapter,
            this.mockAnonCloudConsumerCurator, this.mockPoolCurator, this.mockJobManager,
            this.mockTokenGenerator, this.mockAnonCloudCertCurator, this.principalProvider,
            this.mockJobStatusCurator);
    }

    @Test
    public void testCloudAuthorizeWithNullCloudRegistrationDTO() {
        assertThrows(BadRequestException.class, () -> cloudRegResource.cloudAuthorize(null, 1));
    }

    @Test
    public void testCloudAuthorizeWithCloudAuthenticationDisabled() {
        doReturn(false).when(mockConfig).getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);

        cloudRegResource = new CloudRegistrationResource(this.mockConfig, this.i18n,
            this.mockCloudRegistrationAdapter, this.mockAnonCloudConsumerCurator, this.mockPoolCurator,
            this.mockJobManager, this.mockTokenGenerator, this.mockAnonCloudCertCurator,
            this.principalProvider, this.mockJobStatusCurator);

        assertThrows(NotImplementedException.class,
            () -> cloudRegResource.cloudAuthorize(new CloudRegistrationDTO().type("test_type"), 1));
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
    public void testCloudAuthorizeWithInvalidCloudAccountIdUsingV2Auth(String cloudAccountId) {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult(cloudAccountId, "instanceId",
            TestUtil.randomString(),
            "ownerKey", "offerId", Set.of("productId"), true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testCloudAuthorizeWithInvalidCloudInstanceIdUsingV2Auth(String instanceId) {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult("cloudAccountId", instanceId,
            TestUtil.randomString(),
            "ownerKey", "offerId", Set.of("productId"), true, false);
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
            "ownerKey", "offerId", Set.of("productId"), true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testCloudAuthorizeWithInvalidOfferIdUsingV2Auth(String offerId) {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult("cloudAccountId", "instanceId",
            TestUtil.randomString(),
            "ownerKey", offerId, Set.of("productId"), true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @Test
    public void testCloudAuthorizeWithNullProductIdsUsingV2Auth() {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult("cloudAccountId", "instanceId",
            TestUtil.randomString(), "ownerKey", "offerId", null, true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));
        assertThrows(NotAuthorizedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @Test
    public void testCloudAuthorizeWithEmptyProductIdsUsingV2Auth() {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult("cloudAccountId", "instanceId",
            TestUtil.randomString(), "ownerKey", "offerId", Set.of(), true, false);
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
            TestUtil.randomString(), expectedOwnerKey, "offerId", Set.of(prodId), true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));
        Owner owner = new Owner().setKey(expectedOwnerKey);
        doReturn(owner).when(mockOwnerCurator).getByKey(expectedOwnerKey);
        doReturn(true).when(mockPoolCurator).hasPoolsForProducts(owner.getKey(), Set.of(prodId));

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

        Set<String> prodIds = Set.of("productId");
        String accountId = "cloudAccountId";
        String offeringId = "offerId";
        CloudAuthenticationResult result = buildMockAuthResult(accountId, "instanceId",
            TestUtil.randomString(), null, offeringId, prodIds, true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId("instanceId")
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(prodIds);
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
        CloudAuthenticationResult result = buildMockAuthResult(accountId, "instanceId",
            TestUtil.randomString(), expectedOwnerKey, "offerId", Set.of(prodId), false, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));
        Owner owner = new Owner().setKey(expectedOwnerKey);
        doReturn(owner).when(mockOwnerCurator).getByKey(expectedOwnerKey);
        doReturn(true).when(mockPoolCurator).hasPoolsForProducts(owner.getKey(), List.of(prodId));

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId("instanceId")
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(Set.of(prodId));
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
        Set<String> prodIds = Set.of("productId");
        String accountId = "cloudAccountId";
        CloudAuthenticationResult result = buildMockAuthResult(accountId, "instanceId",
            TestUtil.randomString(), expectedOwnerKey, "offerId", prodIds, true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId("instanceId")
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(prodIds);
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
        CloudAuthenticationResult result = buildMockAuthResult(accountId, "instanceId",
            TestUtil.randomString(), expectedOwnerKey, "offerId", Set.of(prodId), true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));
        Owner owner = new Owner().setKey(expectedOwnerKey);
        doReturn(owner).when(mockOwnerCurator).getByKey(expectedOwnerKey);
        doReturn(false).when(mockPoolCurator).hasPoolsForProducts(owner.getKey(), List.of(prodId));

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId("instanceId")
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(Set.of(prodId));
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
        CloudAuthenticationResult result = buildMockAuthResult(accountId, instanceId, TestUtil.randomString(),
            expectedOwnerKey, "offerId", Set.of(prodId), true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));
        Owner owner = new Owner().setKey(expectedOwnerKey);
        doReturn(owner).when(mockOwnerCurator).getByKey(expectedOwnerKey);
        doReturn(false).when(mockPoolCurator).hasPoolsForProducts(owner.getKey(), List.of(prodId));

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId(instanceId)
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(Set.of(prodId));
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
    public void testCloudAuthorizeWithNoExistingOwnerFromAdapterAndExistingAnonConsumerUsingV2Auth() {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String prodId = "productId";
        String accountId = "cloudAccountId";
        String instanceId = "instanceId";
        CloudAuthenticationResult result = buildMockAuthResult(accountId, instanceId, TestUtil.randomString(),
            null, "offerId", Set.of(prodId), true, false);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        AnonymousCloudConsumer anonConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId(accountId)
            .setCloudInstanceId(instanceId)
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(Set.of(prodId));
        doReturn(anonConsumer).when(mockAnonCloudConsumerCurator).getByCloudInstanceId(instanceId);

        String expectedToken = "anon-token";
        doReturn(expectedToken).when(mockTokenGenerator).buildAnonymousRegistrationToken(principal,
            anonConsumer.getUuid());

        Response response = cloudRegResource.cloudAuthorize(dto, 2);

        // If there is no upstream owner, we have no reason to check for pools downstream yet
        verify(mockPoolCurator, never()).hasPoolsForProducts(any(), any());

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
    }

    @Test
    public void testAuthorizeWithUnknownVersion() {
        assertThrows(BadRequestException.class,
            () -> cloudRegResource.cloudAuthorize(new CloudRegistrationDTO().type("test_type"), 100));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testCloudAuthorizeV2WithRegistrationOnlyAndMissingOwnerKey(String ownerKey) {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        CloudAuthenticationResult result = buildMockAuthResult(TestUtil.randomString(),
            TestUtil.randomString(), TestUtil.randomString(), ownerKey, TestUtil.randomString(),
            Set.of(TestUtil.randomString()), false, true);
        doReturn(result).when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotImplementedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @Test
    public void testCloudAuthorizeV2ForUnsupportedCloudOffering() {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        doThrow(new CloudRegistrationNotSupportedForOfferingException())
            .when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        assertThrows(NotImplementedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @Test
    public void testCloudAuthorizeV2WithRegistrationOnly() {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String expectedOwnerKey = "ownerKey";
        CloudAuthenticationResult result = buildMockAuthResult(null, null, null, expectedOwnerKey, null,
            null, false, true);
        doReturn(result)
            .when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        String expectedToken = TestUtil.randomString();
        doReturn(expectedToken)
            .when(mockTokenGenerator)
            .buildStandardRegistrationToken(principal, expectedOwnerKey);

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
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testCloudAuthorizeV2WithRegistrationOnlyAndInvalidOwnerKey(String ownerKey) {
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        String expectedOwnerKey = "ownerKey";
        CloudAuthenticationResult result = buildMockAuthResult(null, null, null, ownerKey, null,
            null, false, true);
        doReturn(result)
            .when(mockCloudRegistrationAdapter)
            .resolveCloudRegistrationDataV2(getCloudRegistrationData(dto));

        String expectedToken = TestUtil.randomString();
        doReturn(expectedToken)
            .when(mockTokenGenerator)
            .buildStandardRegistrationToken(principal, expectedOwnerKey);

        assertThrows(NotImplementedException.class, () -> cloudRegResource.cloudAuthorize(dto, 2));
    }

    @Test
    void testCancelCloudAccountJobsValidAccountId() {
        String cloudAccountId = TestUtil.randomString("validCloudAccountId-");
        String jobId1 = TestUtil.randomString("jobId-");
        String jobId2 = TestUtil.randomString("jobId-");
        List<AsyncJobStatus> jobs = Arrays.asList(
            createMockJob(jobId1, cloudAccountId), createMockJob(jobId2, cloudAccountId)
        );
        when(mockJobManager.findJobs(any())).thenReturn(jobs);
        when(mockJobManager.cancelJob(any())).thenReturn(new AsyncJobStatus());

        List<String> canceledJobIds = cloudRegResource.cancelCloudAccountJobs(cloudAccountId);

        assertEquals(List.of(jobId1, jobId2), canceledJobIds);
    }

    @Test
    void testCancelCloudAccountJobsNoJobsToCancel() {
        String cloudAccountId = "validAccountId";
        when(mockJobManager.findJobs(any())).thenReturn(Collections.emptyList());

        List<String> canceledJobIds = cloudRegResource.cancelCloudAccountJobs(cloudAccountId);

        assertThat(canceledJobIds).isEmpty();
    }

    @Test
    void testCancelJobsShouldThrowBadRequestWithNullId() {
        assertThrows(BadRequestException.class, () -> cloudRegResource.cancelCloudAccountJobs(null));
    }

    @Test
    void testCancelJobsShouldThrowBadRequestWithEmptyId() {
        assertThrows(BadRequestException.class, () -> cloudRegResource.cancelCloudAccountJobs(""));
    }

    @Test
    void testValidOptionsForJobStatuses() {
        String cloudAccountId = TestUtil.randomString("validCloudAccountId-");
        List<AsyncJobStatus> jobs = Collections.emptyList();
        when(mockJobManager.findJobs(any())).thenReturn(jobs);

        cloudRegResource.cancelCloudAccountJobs(cloudAccountId);

        verify(mockJobManager, times(1)).findJobs(argThat(argument ->
            argument.getJobStates().containsAll(Arrays.asList(
                AsyncJobStatus.JobState.CREATED,
                AsyncJobStatus.JobState.QUEUED,
                AsyncJobStatus.JobState.RUNNING,
                AsyncJobStatus.JobState.SCHEDULED,
                AsyncJobStatus.JobState.WAITING,
                AsyncJobStatus.JobState.FAILED_WITH_RETRY
            ))
        ));
    }

    @Test
    void testCancelingJobInTerminalState() {
        String cloudAccountId = TestUtil.randomString("validCloudAccountId-");
        String jobId = TestUtil.randomString("jobId-");
        List<AsyncJobStatus> jobs = List.of(createMockJob(jobId, cloudAccountId));

        when(mockJobManager.findJobs(any())).thenReturn(jobs);
        when(mockJobManager.cancelJob(jobId)).thenThrow(IllegalStateException.class);

        List<String> cancelledJobIds = cloudRegResource.cancelCloudAccountJobs(cloudAccountId);

        verify(mockJobManager, times(1)).cancelJob(jobId);
        assertThat(cancelledJobIds).isEmpty();
    }

    @Test
    void testDeleteAnonymousConsumers() {
        String accountId = "test-account-id";
        AnonymousCloudConsumer consumer = mock(AnonymousCloudConsumer.class);
        doReturn(List.of(consumer)).when(mockAnonCloudConsumerCurator).getByCloudAccountId(accountId);
        doReturn(mock(AnonymousContentAccessCertificate.class)).when(consumer).getContentAccessCert();

        cloudRegResource.deleteAnonymousConsumersByAccountId(accountId);
        verify(this.mockAnonCloudConsumerCurator, Mockito.times(1))
            .delete(consumer);
        verify(this.mockAnonCloudCertCurator, Mockito.times(1))
            .delete(any(AnonymousContentAccessCertificate.class));
    }

    private CloudRegistrationData getCloudRegistrationData(CloudRegistrationDTO cloudRegistrationDTO) {
        CloudRegistrationData registrationData = new CloudRegistrationData();
        registrationData.setType(cloudRegistrationDTO.getType());
        registrationData.setMetadata(cloudRegistrationDTO.getMetadata());
        registrationData.setSignature(cloudRegistrationDTO.getSignature());
        return registrationData;
    }

    private CloudAuthenticationResult buildMockAuthResult(String cloudAccountId, String instanceId,
        String provider, String ownerKey,
        String offerId, Set<String> productIds, boolean isEntitled, boolean isRegistrationOnly) {
        CloudAuthenticationResult mockResult = mock(CloudAuthenticationResult.class);
        doReturn(cloudAccountId).when(mockResult).getCloudAccountId();
        doReturn(instanceId).when(mockResult).getCloudInstanceId();
        doReturn(provider).when(mockResult).getCloudProvider();
        doReturn(ownerKey).when(mockResult).getOwnerKey();
        doReturn(offerId).when(mockResult).getOfferId();
        doReturn(productIds).when(mockResult).getProductIds();
        doReturn(isEntitled).when(mockResult).isEntitled();
        doReturn(isRegistrationOnly).when(mockResult).isRegistrationOnly();

        return mockResult;
    }

    private AsyncJobStatus createMockJob(String jobId, String cloudAccountId) {
        AsyncJobStatus job = Mockito.mock(AsyncJobStatus.class);
        Mockito.when(job.getId()).thenReturn(jobId);

        Map<String, String> arguments = new HashMap<>();
        arguments.put("cloud_account_id",
            ObjectMapperFactory.getObjectMapper().writeValueAsString(cloudAccountId));

        JobArguments jobArguments = new JobArguments(arguments);
        Mockito.when(job.getJobArguments()).thenReturn(jobArguments);

        return job;
    }

}
