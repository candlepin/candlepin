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

import static org.quartz.JobBuilder.newJob;

import org.candlepin.auth.Principal;
import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.pinsetter.tasks.RegenEnvEntitlementCertsJob;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST API for managing Environments.
 */
@Path("/environments")
@Api("environments")
public class EnvironmentResource {

    private static Logger log = LoggerFactory.getLogger(AdminResource.class);

    private EnvironmentCurator envCurator;
    private I18n i18n;
    private EnvironmentContentCurator envContentCurator;
    private ConsumerResource consumerResource;
    private PoolManager poolManager;
    private ConsumerCurator consumerCurator;
    private ContentCurator contentCurator;

    @Inject
    public EnvironmentResource(EnvironmentCurator envCurator, I18n i18n,
        EnvironmentContentCurator envContentCurator, ConsumerResource consumerResource,
        PoolManager poolManager, ConsumerCurator consumerCurator, ContentCurator contentCurator) {

        this.envCurator = envCurator;
        this.i18n = i18n;
        this.envContentCurator = envContentCurator;
        this.consumerResource = consumerResource;
        this.poolManager = poolManager;
        this.consumerCurator = consumerCurator;
        this.contentCurator = contentCurator;
    }

    @ApiOperation(notes = "Retrieves a single Environment", value = "getEnv")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("/{env_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Environment getEnv(
        @PathParam("env_id") @Verify(Environment.class) String envId) {
        Environment e = envCurator.find(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }
        return e;
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
        Environment e = envCurator.find(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }

        // Cleanup all consumers and their entitlements:
        log.info("Deleting consumers in environment {}", e);
        for (Consumer c : e.getConsumers()) {
            log.info("Deleting consumer: {}", c);

            poolManager.revokeAllEntitlements(c);
            consumerCurator.delete(c);
        }

        log.info("Deleting environment: {}", e);
        envCurator.delete(e);
    }

    @ApiOperation(notes = "Lists the Environments.  Only available to super admins.",
        value = "getEnvironments")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "environments")
    public List<Environment> getEnvironments() {
        return envCurator.listAll();
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

        Content resolved = this.contentCurator.lookupById(environment.getOwner(), contentId);

        if (resolved == null) {
            throw new NotFoundException(i18n.tr(
                "Unable to find content with the ID \"{0}\".", contentId
            ));
        }

        return resolved;
    }


    @ApiOperation(notes = "Promotes a Content into an Environment. This call accepts multiple " +
        "content sets to promote at once, after which all affected certificates for consumers" +
        " in the enironment will be regenerated. Consumers registered to this environment " +
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
    public JobDetail promoteContent(
        @PathParam("env_id") @Verify(Environment.class) String envId,
        List<org.candlepin.model.dto.EnvironmentContent> contentToPromote,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        Environment env = lookupEnvironment(envId);

        Set<String> contentIds = new HashSet<String>();
        // Make sure this content has not already been promoted within this environment
        // Impl note:
        // We have to do this in a separate loop or we'll end up with an undefined state, should
        // there be a problem with the request.
        for (org.candlepin.model.dto.EnvironmentContent promoteMe : contentToPromote) {
            log.debug(
                "EnvironmentContent to promote: {}:{}",
                promoteMe.getEnvironmentId(), promoteMe.getContentId()
            );

            EnvironmentContent existing = this.envContentCurator.lookupByEnvironmentAndContent(
                env, promoteMe.getContentId()
            );

            if (existing != null) {
                throw new ConflictException(i18n.tr(
                    "The content with id {0} has already been promoted in this environment.",
                    promoteMe.getContentId()
                ));
            }
        }

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

        JobDataMap map = new JobDataMap();
        map.put(RegenEnvEntitlementCertsJob.ENV, env);
        map.put(RegenEnvEntitlementCertsJob.CONTENT, contentIds);
        map.put(RegenEnvEntitlementCertsJob.LAZY_REGEN, lazyRegen);

        JobDetail detail = newJob(RegenEnvEntitlementCertsJob.class)
            .withIdentity("regen_entitlement_cert_of_env" + Util.generateUUID())
            .usingJobData(map)
            .build();

        return detail;
    }

    @ApiOperation(notes = "Demotes a Content from an Environment. Consumer's registered to " +
        "this environment will no see this content in their entitlement certificates. (after" +
        " they are regenerated and synced to clients) This call accepts multiple content IDs" +
        " to demote at once, allowing us to mass demote, then trigger a cert regeneration." +
        " NOTE: This call expects the actual content IDs, *not* the ID created for each " +
        "EnvironmentContent object created after a promotion. This is to help integrate " +
        "with other management apps which should not have to track/lookup a specific ID " +
        "for the content to demote.", value = "demoteContent")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{env_id}/content")
    public JobDetail demoteContent(
        @PathParam("env_id") @Verify(Environment.class) String envId,
        @QueryParam("content") String[] contentIds,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        Environment e = lookupEnvironment(envId);
        Map<String, EnvironmentContent> demotedContent = new HashMap<String, EnvironmentContent>();

        // Step through and validate all given content IDs before deleting
        for (String contentId : contentIds) {
            EnvironmentContent envContent = envContentCurator.lookupByEnvironmentAndContent(e, contentId);

            if (envContent == null) {
                throw new NotFoundException(i18n.tr("Content does not exist in environment: {0}", contentId));
            }

            demotedContent.put(contentId, envContent);
        }

        // We've validated everything here, so we're clear to start deleting...
        for (EnvironmentContent envContent : demotedContent.values()) {
            this.envContentCurator.delete(envContent);
        }

        // Impl note: Unfortunately, we have to make an additional set here, as the keySet isn't
        // serializable. Attempting to use it causes exceptions.
        Set<String> demotedContentIds = new HashSet<String>(demotedContent.keySet());
        JobDataMap map = new JobDataMap();
        map.put(RegenEnvEntitlementCertsJob.ENV, e);
        map.put(RegenEnvEntitlementCertsJob.CONTENT, demotedContentIds);
        map.put(RegenEnvEntitlementCertsJob.LAZY_REGEN, lazyRegen);

        JobDetail detail = newJob(RegenEnvEntitlementCertsJob.class)
            .withIdentity("regen_entitlement_cert_of_env" + Util.generateUUID())
            .usingJobData(map)
            .build();

        return detail;
    }

    private Environment lookupEnvironment(String envId) {
        Environment e = envCurator.find(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr(
                "No such environment: {0}", envId));
        }
        return e;
    }

    @ApiOperation(notes = "Creates an Environment", value = "create")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole(noAuth = true)
    @Path("/{env_id}/consumers")
    public Consumer create(@PathParam("env_id") String envId, Consumer consumer,
        @Context Principal principal, @QueryParam("username") String userName,
        @QueryParam("owner") String ownerKey,
        @QueryParam("activation_keys") String activationKeys)
        throws BadRequestException {

        Environment e = lookupEnvironment(envId);
        consumer.setEnvironment(e);
        return this.consumerResource.create(consumer, principal, userName,
            e.getOwner().getKey(), activationKeys, true);
    }

}
