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
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.resource.server.v1.CloudRegistrationApi;
import org.candlepin.service.CloudProvider;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.MalformedCloudRegistrationException;
import org.candlepin.service.model.CloudAuthenticationResult;

import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Objects;

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
    private final PoolCurator poolCurator;
    private final JobManager jobManager;
    private final CloudAuthTokenGenerator tokenGenerator;

    private final boolean enabled;

    @Inject
    public CloudRegistrationResource(Configuration config, I18n i18n,
        CloudRegistrationAdapter cloudRegistrationAdapter,
        OwnerCurator ownerCurator, AnonymousCloudConsumerCurator anonymousCloudConsumerCurator,
        PoolCurator poolCurator, JobManager jobManager, CloudAuthTokenGenerator tokenGenerator) {

        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);
        this.cloudRegistrationAdapter = Objects.requireNonNull(cloudRegistrationAdapter);
        this.anonymousCloudConsumerCurator = Objects.requireNonNull(anonymousCloudConsumerCurator);
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);

        this.enabled = this.config.getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);
    }

    @Override
    @SecurityHole(noAuth = true)
    public Response cloudAuthorize(CloudRegistrationDTO cloudRegistrationDTO, Integer version) {
        if (cloudRegistrationDTO == null) {
            throw new BadRequestException(this.i18n.tr("No cloud registration information provided"));
        }

        Principal principal = ResteasyContext.getContextData(Principal.class);
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

                    throw new CloudRegistrationAuthorizationException(errmsg);
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
        catch (CloudRegistrationAuthorizationException e) {
            throw new NotAuthorizedException(e.getMessage());
        }
        catch (MalformedCloudRegistrationException e) {
            throw new BadRequestException(e.getMessage(), e);
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
        validateCloudAuthenticationResult(authResult);

        String cloudInstanceId = authResult.getCloudInstanceId();
        AnonymousCloudConsumer existingAnonConsumer = anonymousCloudConsumerCurator
            .getByCloudInstanceId(cloudInstanceId);

        String ownerKey = authResult.getOwnerKey();

        // verify that the owner exists upstream and is entitled
        boolean createAnonConsumer = false;
        if (ownerKey == null || ownerKey.isBlank() || !authResult.isEntitled()) {
            CloudAccountOrgSetupJobConfig jobConfig = CloudAccountOrgSetupJob.createJobConfig()
                .setCloudAccountId(authResult.getCloudAccountId())
                .setCloudOfferingId(authResult.getOfferId())
                .setCloudProvider(authResult.getCloudProvider());

            if (ownerKey != null && !ownerKey.isBlank()) {
                jobConfig.setOwnerKey(ownerKey);
            }

            try {
                this.jobManager.queueJob(jobConfig);
            }
            catch (JobException e) {
                String errMsg = this.i18n.tr("An unexpected exception occurred " +
                    "while scheduling job \"{0}\"", jobConfig.getJobKey());
                log.error(errMsg, e);
                throw new IseException(errMsg, e);
            }

            if (existingAnonConsumer == null) {
                createAnonConsumer = true;
            }

            log.info(
                "Cloud account org setup job created for account {} {} using offer {}",
                authResult.getCloudProvider().shortName(), authResult.getCloudAccountId(),
                authResult.getOfferId());
        }

        // Impl note: It is possible the owner and subscription exists through the adapters, but
        // the owner and pool do not yet exist in Candlepin. If that is the case we will want to create
        // an anonymous cloud consumer record and provide an anonymous registration token.
        String anonymousConsumerUuid = null;
        if (!createAnonConsumer) {
            anonymousConsumerUuid = existingAnonConsumer == null ? null : existingAnonConsumer.getUuid();
            boolean ownerAndPoolExists = poolCurator.hasPoolForProduct(ownerKey, authResult.getProductId());
            if ((!ownerAndPoolExists && existingAnonConsumer == null)) {
                createAnonConsumer = true;
            }
        }

        if (createAnonConsumer) {
            AnonymousCloudConsumer createdAnonConsumer = new AnonymousCloudConsumer()
                .setCloudAccountId(authResult.getCloudAccountId())
                .setCloudInstanceId(authResult.getCloudInstanceId())
                .setProductId(authResult.getProductId())
                .setCloudProviderShortName(authResult.getCloudProvider().shortName());

            createdAnonConsumer = anonymousCloudConsumerCurator.create(createdAnonConsumer);
            anonymousConsumerUuid = createdAnonConsumer.getUuid();

            log.info("Anonymous consumer created for instance {} using cloud account {} {}",
                authResult.getCloudInstanceId(), authResult.getCloudProvider().shortName(),
                authResult.getCloudAccountId());
        }

        String token = anonymousConsumerUuid == null ?
            tokenGenerator.buildStandardRegistrationToken(principal, ownerKey) :
            tokenGenerator.buildAnonymousRegistrationToken(principal, anonymousConsumerUuid);

        CloudAuthTokenType tokenType = anonymousConsumerUuid == null ?
            CloudAuthTokenType.STANDARD :
            CloudAuthTokenType.ANONYMOUS;

        CloudAuthenticationResultDTO cloudAuthResultDTO = new CloudAuthenticationResultDTO()
            .ownerKey(ownerKey)
            .token(token)
            .tokenType(tokenType.toString());

        if (anonymousConsumerUuid != null) {
            cloudAuthResultDTO.anonymousConsumerUuid(anonymousConsumerUuid);
        }

        return cloudAuthResultDTO;
    }

    /**
     * Validates the values of in {@link CloudAuthenticationResult}.
     *
     * @param result
     *  the {@link CloudAuthenticationResult} to validate
     *
     * @throws CloudRegistrationAuthorizationException
     *  if a required field does no meet the validation requirements
     */
    private void validateCloudAuthenticationResult(CloudAuthenticationResult result) {
        String cloudAccountId = result.getCloudAccountId();
        if (cloudAccountId == null || cloudAccountId.isBlank()) {
            String errmsg = this.i18n.tr("cloud account ID could not be resolved");

            throw new CloudRegistrationAuthorizationException(errmsg);
        }

        String cloudInstanceId = result.getCloudInstanceId();
        if (cloudInstanceId == null || cloudInstanceId.isBlank()) {
            String errmsg = this.i18n.tr("cloud instance ID could not be resolved");

            throw new CloudRegistrationAuthorizationException(errmsg);
        }

        CloudProvider cloudProvider = result.getCloudProvider();
        if (cloudProvider == null) {
            String errmsg = this.i18n.tr("cloud provider could not be resolved");

            throw new CloudRegistrationAuthorizationException(errmsg);
        }

        String offerId = result.getOfferId();
        if (offerId == null || offerId.isBlank()) {
            String errmsg = this.i18n.tr("offer ID could not be resolved");

            throw new CloudRegistrationAuthorizationException(errmsg);
        }

        String productId = result.getProductId();
        if (productId == null || productId.isBlank()) {
            String errmsg = this.i18n.tr("product ID could not be resolved");

            throw new CloudRegistrationAuthorizationException(errmsg);
        }
    }

}
