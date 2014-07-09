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
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
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
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.List;
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

/**
 * REST API for managing Environments.
 */
@Path("/environments")
public class EnvironmentResource {

    private EnvironmentCurator envCurator;
    private I18n i18n;
    private EnvironmentContentCurator envContentCurator;
    private ConsumerResource consumerResource;
    private PoolManager poolManager;
    private ConsumerCurator consumerCurator;

    @Inject
    public EnvironmentResource(EnvironmentCurator envCurator, I18n i18n,
        EnvironmentContentCurator envContentCurator,
        ConsumerResource consumerResource, PoolManager poolManager,
        ConsumerCurator consumerCurator) {

        this.envCurator = envCurator;
        this.i18n = i18n;
        this.envContentCurator = envContentCurator;
        this.consumerResource = consumerResource;
        this.poolManager = poolManager;
        this.consumerCurator = consumerCurator;
    }

    /**
     * Retrieves a single Environment
     *
     * @param envId
     * @httpcode 200
     * @httpcode 404
     * @return an Environment object
     */
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

    /**
     * Deletes an environment.
     * <p>
     * WARNING: this will delete all consumers in the environment and revoke their
     * entitlement certificates.
     *
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("/{env_id}")
    public void deleteEnv(@PathParam("env_id") @Verify(Environment.class) String envId) {
        Environment e = envCurator.find(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }

        // Cleanup all consumers and their entitlements:
        for (Consumer c : e.getConsumers()) {
            poolManager.revokeAllEntitlements(c);
            consumerCurator.delete(c);
        }

        envCurator.delete(e);
    }

    /**
     * Lists the Environments
     * <p>
     * Only available to super admins.
     *
     * @return a list of Environment objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "environments")
    public List<Environment> getEnvironments() {
        return envCurator.listAll();
    }

    /**
     * Promotes a Content into an Environment.
     * <p>
     * This call accepts multiple content sets to promote at once, after which
     * all affected certificates for consumers in the enironment will be
     * regenerated.
     * <p>
     * Consumers registered to this environment will now receive this content in
     * their entitlement certificates.
     * <p>
     * Because the certificate regeneraiton can be quite time consuming, this
     * is done as an asynchronous job. The content will be promoted and immediately
     * available for new entitlements, but existing entitlements could take some time
     * to be regenerated and sent down to clients as they check in.
     *
     * @httpcode 200
     * @httpcode 404
     * @return a A JobDetail object
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{env_id}/content")
    public JobDetail promoteContent(
            @PathParam("env_id") @Verify(Environment.class) String envId,
            List<EnvironmentContent> contentToPromote,
            @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        Environment env = lookupEnvironment(envId);

        Set<String> contentIds = new HashSet<String>();
        for (EnvironmentContent promoteMe : contentToPromote) {
            // Make sure the content exists:
            promoteMe.setContentId(promoteMe.getContentId());
            promoteMe.setEnvironment(env);
            envContentCurator.create(promoteMe);
            env.getEnvironmentContent().add(promoteMe);
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

    /**
     * Demotes a Content from an Environment.
     * <p>
     * Consumer's registered to this environment will no see this content in their
     * entitlement certificates. (after they are regenerated and synced to clients)
     * <p>
     * This call accepts multiple content IDs to demote at once, allowing us to
     * mass demote, then trigger a cert regeneration.
     * <p>
     * NOTE: This call expects the actual content IDs, *not* the ID created for
     * each EnvironmentContent object created after a promotion. This is to help
     * integrate with other management apps which should not have to track/lookup
     * a specific ID for the content to demote.
     *
     * @httpcode 200
     * @httpcode 404
     * @return a JobDetail object
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{env_id}/content")
    public JobDetail demoteContent(
        @PathParam("env_id") @Verify(Environment.class) String envId,
        @QueryParam("content") String[] contentIds,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        Environment e = lookupEnvironment(envId);
        Set<String> demotedContentIds = new HashSet<String>();
        for (String contentId : contentIds) {
            EnvironmentContent envContent =
                envContentCurator.lookupByEnvironmentAndContent(e, contentId);
            envContentCurator.delete(envContent);
            demotedContentIds.add(contentId);
        }

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

    /**
     * Creates an Environment
     *
     * @param envId
     * @param consumer
     * @param principal
     * @param userName
     * @param ownerKey
     * @param activationKeys
     * @return an Environment object
     * @throws BadRequestException if the Environment cannot be created
     */
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
            e.getOwner().getKey(), activationKeys);
    }

}
