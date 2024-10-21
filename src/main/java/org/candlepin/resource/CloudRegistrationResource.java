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

import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.CloudAccountOrgSetupJob;
import org.candlepin.async.tasks.CloudAccountOrgSetupJob.CloudAccountOrgSetupJobConfig;
import org.candlepin.auth.CloudAuthTokenGenerator;
import org.candlepin.auth.CloudAuthTokenType;
import org.candlepin.auth.CloudRegistrationData;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SecurityHole;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.api.server.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.server.v1.CloudRegistrationDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.exceptions.NotImplementedException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryArguments;
import org.candlepin.model.PoolCurator;
import org.candlepin.resource.server.v1.CloudRegistrationApi;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.cloudregistration.CloudRegistrationNotSupportedForOfferingException;
import org.candlepin.service.model.CloudAuthenticationResult;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;



/**
 * End point(s) for cloud registration token generation
 */
public class CloudRegistrationResource implements CloudRegistrationApi {
    private static Logger log = LoggerFactory.getLogger(CloudRegistrationResource.class);

    private final Configuration config;
    private final CloudRegistrationAdapter cloudRegistrationAdapter;
    private final I18n i18n;
    private final AnonymousCloudConsumerCurator anonymousCloudConsumerCurator;
    private final AnonymousContentAccessCertificateCurator anonymousCloudCertCurator;
    private final PoolCurator poolCurator;
    private final JobManager jobManager;
    private final AsyncJobStatusCurator jobStatusCurator;
    private final CloudAuthTokenGenerator tokenGenerator;
    private final PrincipalProvider principalProvider;

    private final boolean enabled;

    @Inject
    public CloudRegistrationResource(Configuration config, I18n i18n,
        CloudRegistrationAdapter cloudRegistrationAdapter,
        AnonymousCloudConsumerCurator anonymousCloudConsumerCurator,
        PoolCurator poolCurator, JobManager jobManager, CloudAuthTokenGenerator tokenGenerator,
        AnonymousContentAccessCertificateCurator anonymousCloudCertCurator,
        PrincipalProvider principalProvider, AsyncJobStatusCurator jobStatusCurator) {

        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);
        this.cloudRegistrationAdapter = Objects.requireNonNull(cloudRegistrationAdapter);
        this.anonymousCloudConsumerCurator = Objects.requireNonNull(anonymousCloudConsumerCurator);
        this.anonymousCloudCertCurator = Objects.requireNonNull(anonymousCloudCertCurator);
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.jobStatusCurator = Objects.requireNonNull(jobStatusCurator);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
        this.principalProvider = Objects.requireNonNull(principalProvider);

        this.enabled = this.config.getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);
    }

    @Override
    @Transactional
    @SecurityHole(noAuth = true)
    public Response cloudAuthorize(CloudRegistrationDTO cloudRegistrationDTO, Integer version) {
        if (cloudRegistrationDTO == null) {
            throw new BadRequestException(this.i18n.tr("No cloud registration information provided"));
        }

        if (cloudRegistrationDTO.getType().isEmpty()) {
            throw new BadRequestException(i18n.tr(
                "Request is missing cloud provider type (e.g. amazon, gcp, azure)"));
        }

        Principal principal = this.principalProvider.get();
        try {
            if (!this.enabled) {
                throw new UnsupportedOperationException(
                    "cloud registration is not enabled on this Candlepin instance");
            }

            CloudRegistrationData registrationData = getCloudRegistrationData(cloudRegistrationDTO);
            if (version == null || version == 1) {
                String ownerKey = this.cloudRegistrationAdapter
                    .resolveCloudRegistrationData(registrationData);
                if (ownerKey == null) {
                    String errmsg = this.i18n
                        .tr("cloud provider or account details could not be resolved to an organization");

                    throw new NotAuthorizedException(errmsg);
                }

                String token = tokenGenerator.buildStandardRegistrationToken(principal, ownerKey);

                return Response.status(Response.Status.OK)
                    .type(MediaType.TEXT_PLAIN)
                    .entity(token)
                    .build();
            }
            else if (version == 2) {
                CloudAuthenticationResult authResult = this.cloudRegistrationAdapter
                    .resolveCloudRegistrationDataV2(registrationData);

                validateCloudAuthenticationResult(authResult);

                CloudAuthenticationResultDTO resultDTO = processAdapterAuthResult(principal, authResult);

                return Response.status(Response.Status.OK)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(resultDTO)
                    .build();
            }
            else {
                String errmsg = this.i18n.tr("unknown cloud authorization version");
                throw new BadRequestException(errmsg);
            }
        }
        catch (UnsupportedOperationException e) {
            String errmsg = this.i18n.tr("Cloud registration is not supported by this Candlepin instance");
            throw new NotImplementedException(errmsg, e);
        }
        catch (CloudRegistrationNotSupportedForOfferingException e) {
            String errmsg = this.i18n.tr("Cloud registration is not supported for the type of " +
                "offering the client is using");
            throw new NotImplementedException(errmsg, e);
        }
    }

    /**
     *  Allows us to cancel the CloudOrgSetupJobs for the specified cloud account ID
     *  This is only for testing purposes.
     *
     * @param cloudAccountId
     */
    @Override
    @Transactional
    public List<String> cancelCloudAccountJobs(String cloudAccountId) {
        if (cloudAccountId == null || cloudAccountId.isBlank()) {
            throw new BadRequestException(this.i18n.tr("Cloud account ID is null or empty"));
        }

        Map<String, String> jobArgs = Map.of("cloud_account_id", cloudAccountId);
        List<String> jobIds = jobStatusCurator.fetchJobIdsByArguments(CloudAccountOrgSetupJob.JOB_KEY,
            jobArgs);
        AsyncJobStatusQueryArguments asyncJobStatusQueryArguments = new AsyncJobStatusQueryArguments();
        asyncJobStatusQueryArguments.setJobIds(jobIds)
            .setJobStates(AsyncJobStatus.JobState.CREATED, AsyncJobStatus.JobState.QUEUED,
                AsyncJobStatus.JobState.RUNNING, AsyncJobStatus.JobState.SCHEDULED,
                AsyncJobStatus.JobState.WAITING, AsyncJobStatus.JobState.FAILED_WITH_RETRY);
        List<AsyncJobStatus> jobs = this.jobManager.findJobs(asyncJobStatusQueryArguments);

        List<String> cancelledJobIds = new ArrayList<>();
        jobs.forEach(job -> {
            try {
                AsyncJobStatus cancelledJobStatus = jobManager.cancelJob(job.getId());
                if (cancelledJobStatus != null) {
                    cancelledJobIds.add(job.getId());
                }
            }
            catch (IllegalStateException e) {
                log.info("{}", e.getMessage());
            }
        });

        return cancelledJobIds;
    }

    /**
     * Allows the removal of anonymous consumers for an entire cloud account.
     *  This is only for testing purposes.
     *
     * @param cloudAccountId
     */
    @Override
    @Transactional
    public void deleteAnonymousConsumersByAccountId(String cloudAccountId) {
        List<AnonymousCloudConsumer> consumers = anonymousCloudConsumerCurator
            .getByCloudAccountId(cloudAccountId);
        if (consumers != null) {
            for (AnonymousCloudConsumer consumer : consumers) {
                anonymousCloudCertCurator.delete(consumer.getContentAccessCert());
                anonymousCloudConsumerCurator.delete(consumer);
            }
        }
    }

    private CloudRegistrationData getCloudRegistrationData(CloudRegistrationDTO cloudRegistrationDTO) {
        CloudRegistrationData registrationData = new CloudRegistrationData();
        registrationData.setType(cloudRegistrationDTO.getType());
        registrationData.setMetadata(cloudRegistrationDTO.getMetadata());
        registrationData.setSignature(cloudRegistrationDTO.getSignature());
        return registrationData;
    }

    private CloudAuthenticationResultDTO processAdapterAuthResult(Principal principal,
        CloudAuthenticationResult authResult) {

        // verify that the owner exists upstream and is entitled
        String ownerKey = authResult.getOwnerKey();
        boolean isOwnerReadyForRegistration = false;
        if (authResult.isRegistrationOnly()) {
            isOwnerReadyForRegistration = true;
        }
        else if (ownerKey == null || ownerKey.isBlank() || !authResult.isEntitled()) {
            CloudAccountOrgSetupJobConfig jobConfig = CloudAccountOrgSetupJob.createJobConfig()
                .setCloudAccountId(authResult.getCloudAccountId())
                .setCloudOfferingId(authResult.getOfferId())
                .setCloudProvider(authResult.getCloudProvider());

            try {
                this.jobManager.queueJob(jobConfig);
            }
            catch (JobException e) {
                String errMsg = this.i18n.tr("An unexpected exception occurred " +
                    "while scheduling job \"{0}\"", jobConfig.getJobKey());
                log.error(errMsg, e);
                throw new IseException(errMsg, e);
            }

            log.info(
                "Cloud account org setup job created for account {} {} using offer {}",
                authResult.getCloudProvider(), authResult.getCloudAccountId(),
                authResult.getOfferId());
        }
        else {
            // Else, check if it exists and is entitled in Candlepin too
            isOwnerReadyForRegistration = poolCurator
                 .hasPoolsForProducts(ownerKey, authResult.getProductIds());
        }

        CloudAuthenticationResultDTO cloudAuthResultDTO;
        if (isOwnerReadyForRegistration) {
            String token = tokenGenerator.buildStandardRegistrationToken(principal, ownerKey);
            CloudAuthTokenType tokenType = CloudAuthTokenType.STANDARD;

            cloudAuthResultDTO = new CloudAuthenticationResultDTO()
                .ownerKey(ownerKey)
                .token(token)
                .tokenType(tokenType.toString());
        }
        else {
            AnonymousCloudConsumer existingAnonConsumer = anonymousCloudConsumerCurator
                .getByCloudInstanceId(authResult.getCloudInstanceId());

            String anonymousConsumerUuid;
            if (existingAnonConsumer == null) {
                AnonymousCloudConsumer createdAnonConsumer = new AnonymousCloudConsumer()
                    .setCloudAccountId(authResult.getCloudAccountId())
                    .setCloudInstanceId(authResult.getCloudInstanceId())
                    .setCloudOfferingId(authResult.getOfferId())
                    .setProductIds(authResult.getProductIds())
                    .setCloudProviderShortName(authResult.getCloudProvider());

                createdAnonConsumer = anonymousCloudConsumerCurator.create(createdAnonConsumer);
                anonymousConsumerUuid = createdAnonConsumer.getUuid();

                log.info("Anonymous consumer created for instance {} using cloud account {} {}",
                    authResult.getCloudInstanceId(), authResult.getCloudProvider(),
                    authResult.getCloudAccountId());
            }
            else {
                anonymousConsumerUuid = existingAnonConsumer.getUuid();
                log.info("Anonymous consumer already exists for instance {} using cloud account {} {}",
                    authResult.getCloudInstanceId(), authResult.getCloudProvider(),
                    authResult.getCloudAccountId());
            }

            String token = tokenGenerator.buildAnonymousRegistrationToken(principal, anonymousConsumerUuid);
            CloudAuthTokenType tokenType = CloudAuthTokenType.ANONYMOUS;

            cloudAuthResultDTO = new CloudAuthenticationResultDTO()
                .ownerKey(ownerKey)
                .token(token)
                .tokenType(tokenType.toString())
                .anonymousConsumerUuid(anonymousConsumerUuid);
        }

        return cloudAuthResultDTO;
    }

    /**
     * Validates the values in {@link CloudAuthenticationResult}.
     *
     * @param result
     *  the {@link CloudAuthenticationResult} to validate
     */
    private void validateCloudAuthenticationResult(CloudAuthenticationResult result) {
        if (result.isRegistrationOnly()) {
            String ownerKey = result.getOwnerKey();
            if (ownerKey == null || ownerKey.isBlank()) {
                String errmsg = this.i18n.tr("Cloud registration is not supported for the type of " +
                    "offering the client is using");

                throw new NotImplementedException(errmsg);
            }

            return;
        }

        String cloudAccountId = result.getCloudAccountId();
        if (cloudAccountId == null || cloudAccountId.isBlank()) {
            String errmsg = this.i18n.tr("cloud account ID could not be resolved");

            throw new NotAuthorizedException(errmsg);
        }

        String cloudInstanceId = result.getCloudInstanceId();
        if (cloudInstanceId == null || cloudInstanceId.isBlank()) {
            String errmsg = this.i18n.tr("cloud instance ID could not be resolved");

            throw new NotAuthorizedException(errmsg);
        }

        String cloudProvider = result.getCloudProvider();
        if (cloudProvider == null || cloudProvider.isBlank()) {
            String errmsg = this.i18n.tr("cloud provider could not be resolved");

            throw new NotAuthorizedException(errmsg);
        }

        String offerId = result.getOfferId();
        if (offerId == null || offerId.isBlank()) {
            String errmsg = this.i18n.tr("offer ID could not be resolved");

            throw new NotAuthorizedException(errmsg);
        }

        Set<String> productIds = result.getProductIds();
        if (productIds == null || productIds.isEmpty()) {
            String errmsg = this.i18n.tr("product IDs could not be resolved");

            throw new NotAuthorizedException(errmsg);
        }
    }
}
