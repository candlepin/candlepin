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

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.RegenEnvEntitlementCertsJob;
import org.candlepin.auth.Principal;
import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.EnvironmentDTO;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Content;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerEnvContentAccessCurator;
import org.candlepin.util.RdbmsExceptionTranslator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * REST API for managing Environments.
 */
@Path("/environments")
@Api(value = "environments", authorizations = { @Authorization("basic") })
public class EnvironmentResource {
    private static Logger log = LoggerFactory.getLogger(AdminResource.class);

    private EnvironmentCurator envCurator;
    private I18n i18n;
    private EnvironmentContentCurator envContentCurator;
    private ConsumerResource consumerResource;
    private PoolManager poolManager;
    private ConsumerCurator consumerCurator;
    private OwnerContentCurator ownerContentCurator;
    private OwnerEnvContentAccessCurator ownerEnvContentAccessCurator;
    private RdbmsExceptionTranslator rdbmsExceptionTranslator;
    private ModelTranslator translator;
    private JobManager jobManager;

    @Inject
    public EnvironmentResource(EnvironmentCurator envCurator, I18n i18n,
        EnvironmentContentCurator envContentCurator, ConsumerResource consumerResource,
        PoolManager poolManager, ConsumerCurator consumerCurator, OwnerContentCurator ownerContentCurator,
        RdbmsExceptionTranslator rdbmsExceptionTranslator,
        OwnerEnvContentAccessCurator ownerEnvContentAccessCurator, ModelTranslator translator,
        JobManager jobManager) {

        this.envCurator = envCurator;
        this.i18n = i18n;
        this.envContentCurator = envContentCurator;
        this.consumerResource = consumerResource;
        this.poolManager = poolManager;
        this.consumerCurator = consumerCurator;
        this.ownerContentCurator = ownerContentCurator;
        this.rdbmsExceptionTranslator = rdbmsExceptionTranslator;
        this.ownerEnvContentAccessCurator = ownerEnvContentAccessCurator;
        this.translator = translator;
        this.jobManager = jobManager;
    }

    @ApiOperation(notes = "Retrieves a single Environment", value = "getEnv")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("/{env_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public EnvironmentDTO getEnv(
        @PathParam("env_id") @Verify(Environment.class) String envId) {
        Environment e = envCurator.get(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }
        return translator.translate(e, EnvironmentDTO.class);
    }

    @ApiOperation(
        notes = "Deletes an environment. WARNING: this will delete all consumers in the environment and " +
        "revoke their entitlement certificates.",
        value = "deleteEnv")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.WILDCARD)
    @Path("/{env_id}")
    public void deleteEnv(@PathParam("env_id") @Verify(Environment.class) String envId) {
        Environment e = envCurator.get(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }

        CandlepinQuery<Consumer> consumers = this.envCurator.getEnvironmentConsumers(e);

        // Cleanup all consumers and their entitlements:
        log.info("Deleting consumers in environment {}", e);
        for (Consumer c : consumers.list()) {
            log.info("Deleting consumer: {}", c);

            // We're about to delete these consumers; no need to regen/dirty their dependent
            // entitlements or recalculate status.
            poolManager.revokeAllEntitlements(c, false);
            consumerCurator.delete(c);
        }

        log.info("Deleting environment: {}", e);
        envCurator.delete(e);
    }

    @ApiOperation(notes = "Lists the Environments.  Only available to super admins.",
        value = "getEnvironments", response = EnvironmentDTO.class, responseContainer = "list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "environments")
    public CandlepinQuery<EnvironmentDTO> getEnvironments() {
        return translator.translateQuery(this.envCurator.listAll(), EnvironmentDTO.class);
    }

    /**
     * Verifies that the content specified by the given content object's ID exists.
     *
     * @param environment
     *  The environment with which the content will be associated
     *
     * @param contentId
     *  The ID of the content to resolve
     *
     * @return
     *  the resolved content instance.
     */
    private Content resolveContent(Environment environment, String contentId) {
        if (environment == null || environment.getOwner() == null) {
            throw new BadRequestException(
                i18n.tr("No environment specified, or environment lacks owner information")
            );
        }

        if (contentId == null) {
            throw new BadRequestException(
                i18n.tr("No content ID specified")
            );
        }

        Content resolved = this.ownerContentCurator.getContentById(environment.getOwner(), contentId);

        if (resolved == null) {
            throw new NotFoundException(i18n.tr(
                "Unable to find content with the ID \"{0}\".", contentId
            ));
        }

        return resolved;
    }

    @ApiOperation(notes = "Promotes a Content into an Environment. This call accepts multiple " +
        "content sets to promote at once, after which all affected certificates for consumers" +
        " in the environment will be regenerated. Consumers registered to this environment " +
        "will now receive this content in their entitlement certificates. Because the" +
        " certificate regeneraiton can be quite time consuming, this is done as an " +
        "asynchronous job. The content will be promoted and immediately available for new " +
        "entitlements, but existing entitlements could take some time to be regenerated and " +
        "sent down to clients as they check in.", value = "promoteContent")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{env_id}/content")
    public AsyncJobStatusDTO promoteContent(
        @PathParam("env_id") @Verify(Environment.class) String envId,
        @ApiParam(name = "contentToPromote", required = true)
        List<org.candlepin.model.dto.EnvironmentContent> contentToPromote,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) throws JobException {

        Environment env = lookupEnvironment(envId);

        // Make sure this content has not already been promoted within this environment
        // Impl note:
        // We have to do this in a separate loop or we'll end up with an undefined state, should
        // there be a problem with the request.
        for (org.candlepin.model.dto.EnvironmentContent promoteMe : contentToPromote) {
            log.debug(
                "EnvironmentContent to promote: {}:{}",
                promoteMe.getEnvironmentId(), promoteMe.getContentId()
            );

            EnvironmentContent existing = this.envContentCurator.getByEnvironmentAndContent(
                env, promoteMe.getContentId()
            );

            if (existing != null) {
                throw new ConflictException(i18n.tr(
                    "The content with id {0} has already been promoted in this environment.",
                    promoteMe.getContentId()
                ));
            }
        }

        Set<String> contentIds = new HashSet<>();

        try {
            contentIds = batchCreate(contentToPromote, env);
            clearContentAccessCerts(env);
        }
        catch (PersistenceException pe) {
            if (rdbmsExceptionTranslator.isConstraintViolationDuplicateEntry(pe)) {
                log.info("Concurrent content promotion will cause this request to fail.",
                    pe);
                throw new ConflictException(
                    i18n.tr("Some of the content is already associated with Environment: {0}",
                        contentToPromote));
            }
            else {
                throw pe;
            }
        }

        JobConfig config = RegenEnvEntitlementCertsJob.createJobConfig()
            .setEnvironment(env)
            .setContent(contentIds)
            .setLazyRegeneration(lazyRegen)
            .setOwner(env.getOwner());

        AsyncJobStatus job = this.jobManager.queueJob(config);
        return this.translator.translate(job, AsyncJobStatusDTO.class);
    }

    @ApiOperation(notes = "Demotes a Content from an Environment. Consumer's registered to " +
        "this environment will no see this content in their entitlement certificates. (after" +
        " they are regenerated and synced to clients) This call accepts multiple content IDs" +
        " to demote at once, allowing us to mass demote, then trigger a cert regeneration." +
        " NOTE: This call expects the actual content IDs, *not* the ID created for each " +
        "EnvironmentContent object created after a promotion. This is to help integrate " +
        "with other management apps which should not have to track/lookup a specific ID " +
        "for the content to demote.", value = "demoteContent")
    @ApiResponses({ @ApiResponse(code = 404, message = "When the content has already been demoted.") })
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{env_id}/content")
    public AsyncJobStatusDTO demoteContent(
        @PathParam("env_id") @Verify(Environment.class) String envId,
        @QueryParam("content") String[] contentIds,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) throws JobException {

        Environment e = lookupEnvironment(envId);
        Map<String, EnvironmentContent> demotedContent = new HashMap<>();

        // Step through and validate all given content IDs before deleting
        for (String contentId : contentIds) {
            EnvironmentContent envContent = envContentCurator.getByEnvironmentAndContent(e, contentId);

            if (envContent == null) {
                throw new NotFoundException(i18n.tr("Content does not exist in environment: {0}", contentId));
            }

            demotedContent.put(contentId, envContent);
        }

        try {
            envContentCurator.bulkDelete(demotedContent.values());
            clearContentAccessCerts(e);
        }
        catch (RollbackException hibernateException) {
            if (rdbmsExceptionTranslator.isUpdateHadNoEffectException(hibernateException)) {
                log.info("Concurrent content demotion will cause this request to fail.", hibernateException);
                throw new NotFoundException(
                    i18n.tr("One of the content does not exist in the environment anymore: {0}",
                        demotedContent.values()));
            }
            else {
                throw hibernateException;
            }
        }

        // Impl note: Unfortunately, we have to make an additional set here, as the keySet isn't
        // serializable. Attempting to use it causes exceptions.
        Set<String> demotedContentIds = new HashSet<>(demotedContent.keySet());
        JobConfig config = RegenEnvEntitlementCertsJob.createJobConfig()
            .setEnvironment(e)
            .setContent(demotedContentIds)
            .setLazyRegeneration(lazyRegen)
            .setOwner(e.getOwner());

        AsyncJobStatus job = this.jobManager.queueJob(config);
        return this.translator.translate(job, AsyncJobStatusDTO.class);
    }

    /**
     * To make promotion transactional
     * @param contentToPromote
     * @param env
     * @return contentIds Ids of the promoted content
     */
    @Transactional
    public Set<String>  batchCreate(List<org.candlepin.model.dto.EnvironmentContent> contentToPromote,
        Environment env) {

        Set<String> contentIds = new HashSet<>();

        for (org.candlepin.model.dto.EnvironmentContent promoteMe : contentToPromote) {
            // Make sure the content exists:
            EnvironmentContent envcontent = new EnvironmentContent();

            envcontent.setEnvironment(env);
            envcontent.setContent(this.resolveContent(env, promoteMe.getContentId()));
            envcontent.setEnabled(promoteMe.getEnabled());

            envContentCurator.create(envcontent);
            env.getEnvironmentContent().add(envcontent);
            contentIds.add(promoteMe.getContentId());
        }

        return contentIds;
    }

    @Transactional
    private void clearContentAccessCerts(Environment env) {
        ownerEnvContentAccessCurator.removeAllForEnvironment(env.getId());
    }

    private Environment lookupEnvironment(String envId) {
        Environment e = envCurator.get(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }
        return e;
    }

    @ApiOperation(notes = "Creates an Environment", value = "create")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole(noAuth = true)
    @Path("/{env_id}/consumers")
    public ConsumerDTO create(@PathParam("env_id") String envId,
        @ApiParam(name = "consumer", required = true) ConsumerDTO consumer,
        @Context Principal principal, @QueryParam("username") String userName,
        @QueryParam("owner") String ownerKey,
        @QueryParam("activation_keys") String activationKeys)
        throws BadRequestException {

        Environment e = lookupEnvironment(envId);
        consumer.setEnvironment(translator.translate(e, EnvironmentDTO.class));
        return this.consumerResource.create(consumer, principal, userName, e.getOwner().getKey(),
            activationKeys, true);
    }

}
