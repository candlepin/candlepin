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
import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.v1.EnvironmentDTO;
import org.candlepin.guice.PrincipalProvider;
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
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.util.RdbmsExceptionTranslator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;



/**
 * REST API for managing Environments.
 */
public class EnvironmentResource implements EnvironmentsApi {
    private static final Logger log = LoggerFactory.getLogger(AdminResource.class);

    private final EnvironmentCurator envCurator;
    private final I18n i18n;
    private final EnvironmentContentCurator envContentCurator;
    private final ConsumerResource consumerResource;
    private final PoolManager poolManager;
    private final ConsumerCurator consumerCurator;
    private final OwnerContentCurator ownerContentCurator;
    private final RdbmsExceptionTranslator rdbmsExceptionTranslator;
    private final ModelTranslator translator;
    private final JobManager jobManager;
    private final DTOValidator validator;
    private final PrincipalProvider principalProvider;
    private final ContentAccessManager contentAccessManager;

    @Inject
    public EnvironmentResource(EnvironmentCurator envCurator, I18n i18n,
        EnvironmentContentCurator envContentCurator, ConsumerResource consumerResource,
        PoolManager poolManager, ConsumerCurator consumerCurator, OwnerContentCurator ownerContentCurator,
        RdbmsExceptionTranslator rdbmsExceptionTranslator, ModelTranslator translator,
        JobManager jobManager, DTOValidator validator, PrincipalProvider principalProvider,
        ContentAccessManager contentAccessManager) {

        this.envCurator = Objects.requireNonNull(envCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.envContentCurator = Objects.requireNonNull(envContentCurator);
        this.consumerResource = Objects.requireNonNull(consumerResource);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.ownerContentCurator = Objects.requireNonNull(ownerContentCurator);
        this.rdbmsExceptionTranslator = Objects.requireNonNull(rdbmsExceptionTranslator);
        this.translator = Objects.requireNonNull(translator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.validator = Objects.requireNonNull(validator);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
    }

    /**
     * Attempts to lookup the environment from the given environment ID.
     *
     * @param environmentId
     *  The ID of the environment to lookup
     *
     * @throws NotFoundException
     *  if the given ID cannot be resolved to a valid Environment
     *
     * @return
     *  the environment with the given ID
     */
    private Environment lookupEnvironment(String environmentId) {
        Environment environment = this.envCurator.get(environmentId);
        if (environment == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", environmentId));
        }

        return environment;
    }

    @Override
    public EnvironmentDTO getEnvironment(@Verify(Environment.class) String envId) {
        Environment e = envCurator.get(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }
        return translator.translate(e, EnvironmentDTO.class);
    }

    @Override
    public void deleteEnvironment(@Verify(Environment.class) String envId) {
        Environment environment = envCurator.get(envId);
        if (environment == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }

        CandlepinQuery<Consumer> consumers = this.envCurator.getEnvironmentConsumers(environment);

        // Cleanup all consumers and their entitlements:
        log.info("Deleting consumers in environment {}", environment);
        for (Consumer c : consumers.list()) {
            log.info("Deleting consumer: {}", c);

            // We're about to delete these consumers; no need to regen/dirty their dependent
            // entitlements or recalculate status.
            this.poolManager.revokeAllEntitlements(c, false);
            this.consumerCurator.delete(c);
        }

        log.info("Deleting environment: {}", environment);
        this.envCurator.delete(environment);
    }

    @Override
    public Iterable<EnvironmentDTO> getEnvironments() {
        return translator.translateQuery(this.envCurator.listAll(), EnvironmentDTO.class);
    }

    @Override
    public AsyncJobStatusDTO promoteContent(@Verify(Environment.class) String envId,
        List<ContentToPromoteDTO> contentToPromote, Boolean lazyRegen) {

        Environment environment = this.lookupEnvironment(envId);

        // Make sure this content has not already been promoted within this environment

        // Impl note:
        // We have to do this in a separate loop or we'll end up with an undefined state, should
        // there be a problem with the request.
        for (ContentToPromoteDTO promoteMe : contentToPromote) {
            log.debug("EnvironmentContent to promote: {}:{}",
                promoteMe.getEnvironmentId(), promoteMe.getContentId());

            EnvironmentContent existing = this.envContentCurator
                .getByEnvironmentAndContent(environment, promoteMe.getContentId());

            if (existing != null) {
                throw new ConflictException(i18n.tr(
                    "The content with id {0} has already been promoted in this environment.",
                    promoteMe.getContentId()));
            }
        }

        Set<String> contentIds;
        try {
            contentIds = this.batchCreate(contentToPromote, environment);

            this.contentAccessManager.refreshEnvironmentContentAccessCerts(environment);
            this.contentAccessManager.syncOwnerLastContentUpdate(environment.getOwner());
        }
        catch (PersistenceException pe) {
            if (rdbmsExceptionTranslator.isConstraintViolationDuplicateEntry(pe)) {
                log.info("Concurrent content promotion will cause this request to fail.",
                    pe);

                throw new ConflictException(i18n.tr(
                    "Some of the content is already associated with Environment: {0}",
                    contentToPromote));
            }
            else {
                throw pe;
            }
        }

        return regenCertificates(environment, contentIds, lazyRegen);
    }

    @Override
    public AsyncJobStatusDTO demoteContent(@Verify(Environment.class) String envId,
        List<String> contentIds, Boolean lazyRegen) {

        Environment environment = this.lookupEnvironment(envId);
        Map<String, EnvironmentContent> demotedContent = new HashMap<>();
        List<String> idsToDemote = contentIds == null ? new ArrayList<>() : contentIds;

        // Step through and validate all given content IDs before deleting
        for (String contentId : idsToDemote) {
            EnvironmentContent envContent = envContentCurator
                .getByEnvironmentAndContent(environment, contentId);

            if (envContent == null) {
                throw new NotFoundException(i18n.tr("Content does not exist in environment: {0}", contentId));
            }

            demotedContent.put(contentId, envContent);
        }

        try {
            this.envContentCurator.bulkDelete(demotedContent.values());

            this.contentAccessManager.refreshEnvironmentContentAccessCerts(environment);
            this.contentAccessManager.syncOwnerLastContentUpdate(environment.getOwner());
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
        return regenCertificates(environment, demotedContentIds, lazyRegen);
    }

    @Override
    @SecurityHole(noAuth = true)
    public ConsumerDTO createConsumerInEnvironment(String envId, ConsumerDTO consumer,
        String userName, String ownerKey, String activationKeys) throws BadRequestException {

        this.validator.validateConstraints(consumer);
        this.validator.validateCollectionElementsNotNull(consumer::getInstalledProducts,
            consumer::getGuestIds, consumer::getCapabilities);

        Environment e = lookupEnvironment(envId);
        consumer.setEnvironment(translator.translate(e, EnvironmentDTO.class));
        return this.consumerResource.create(consumer, this.principalProvider.get(), userName,
            e.getOwner().getKey(), activationKeys, true);
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
            throw new BadRequestException(i18n.tr(
                "No environment specified, or environment lacks owner information"));
        }

        if (contentId == null) {
            throw new BadRequestException(i18n.tr("No content ID specified"));
        }

        Content resolved = this.ownerContentCurator.getContentById(environment.getOwner(), contentId);
        if (resolved == null) {
            throw new NotFoundException(i18n.tr("Unable to find content with the ID \"{0}\".", contentId));
        }

        return resolved;
    }

    private AsyncJobStatusDTO regenCertificates(Environment environment, Set<String> demotedContentIds,
        Boolean lazyRegen) {

        JobConfig config = RegenEnvEntitlementCertsJob.createJobConfig()
            .setEnvironment(environment)
            .setContent(demotedContentIds)
            .setLazyRegeneration(lazyRegen)
            .setOwner(environment.getOwner());

        AsyncJobStatus job;
        try {
            job = this.jobManager.queueJob(config);
        }
        catch (JobException e) {
            throw new IseException(this.i18n.tr(
                "An unexpected exception occurred while scheduling job \"{0}\"", config.getJobKey()), e);
        }
        return this.translator.translate(job, AsyncJobStatusDTO.class);
    }

    /**
     * To make promotion transactional
     * @param contentToPromote
     * @param env
     * @return contentIds Ids of the promoted content
     */
    @Transactional
    public Set<String> batchCreate(List<ContentToPromoteDTO> contentToPromote, Environment env) {
        Set<String> contentIds = new HashSet<>();

        for (ContentToPromoteDTO promoteMe : contentToPromote) {
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
}
