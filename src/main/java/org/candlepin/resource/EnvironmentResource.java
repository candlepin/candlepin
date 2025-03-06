/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
import org.candlepin.auth.SecurityHole;
import org.candlepin.auth.Verify;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.EntitlementCertificateService;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.dto.api.server.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.server.v1.EnvironmentDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentContentOverride;
import org.candlepin.model.EnvironmentContentOverrideCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.SCACertificate;
import org.candlepin.resource.server.v1.EnvironmentApi;
import org.candlepin.resource.util.EntitlementEnvironmentFilter;
import org.candlepin.resource.util.EnvironmentUpdates;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.RdbmsExceptionTranslator;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;



/**
 * REST API for managing Environments.
 */
public class EnvironmentResource implements EnvironmentApi {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentResource.class);

    private final EnvironmentCurator envCurator;
    private final I18n i18n;
    private final EnvironmentContentCurator envContentCurator;
    private final ConsumerResource consumerResource;
    private final PoolService poolService;
    private final ConsumerCurator consumerCurator;
    private final ContentCurator contentCurator;
    private final RdbmsExceptionTranslator rdbmsExceptionTranslator;
    private final ModelTranslator translator;
    private final JobManager jobManager;
    private final DTOValidator dtoValidator;
    private final ContentOverrideValidator contentOverrideValidator;
    private final ContentAccessManager contentAccessManager;
    private final CertificateSerialCurator certificateSerialCurator;
    private final IdentityCertificateCurator identityCertificateCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final EntitlementCertificateService entCertService;
    private final EnvironmentContentOverrideCurator envContentOverrideCurator;

    private final EntitlementEnvironmentFilter entitlementEnvironmentFilter;

    @Inject
    public EnvironmentResource(
        EnvironmentCurator envCurator,
        I18n i18n,
        EnvironmentContentCurator envContentCurator,
        ConsumerResource consumerResource,
        PoolService poolService,
        ConsumerCurator consumerCurator,
        ContentCurator contentCurator,
        RdbmsExceptionTranslator rdbmsExceptionTranslator,
        ModelTranslator translator,
        JobManager jobManager,
        DTOValidator dtoValidator,
        ContentOverrideValidator contentOverrideValidator,
        ContentAccessManager contentAccessManager,
        CertificateSerialCurator certificateSerialCurator,
        IdentityCertificateCurator identityCertificateCurator,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        EntitlementCurator entCurator,
        EntitlementCertificateService entCertService,
        EnvironmentContentOverrideCurator envContentOverrideCurator) {

        this.envCurator = Objects.requireNonNull(envCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.envContentCurator = Objects.requireNonNull(envContentCurator);
        this.consumerResource = Objects.requireNonNull(consumerResource);
        this.poolService = Objects.requireNonNull(poolService);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.rdbmsExceptionTranslator = Objects.requireNonNull(rdbmsExceptionTranslator);
        this.translator = Objects.requireNonNull(translator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.dtoValidator = Objects.requireNonNull(dtoValidator);
        this.contentOverrideValidator = Objects.requireNonNull(contentOverrideValidator);
        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
        this.certificateSerialCurator = Objects.requireNonNull(certificateSerialCurator);
        this.identityCertificateCurator = Objects.requireNonNull(identityCertificateCurator);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        Objects.requireNonNull(entCurator);
        this.entCertService = Objects.requireNonNull(entCertService);
        this.envContentOverrideCurator = Objects.requireNonNull(envContentOverrideCurator);

        this.entitlementEnvironmentFilter = new EntitlementEnvironmentFilter(entCurator, envContentCurator);
    }

    /**
     * Attempts to lookup the environment from the given environment ID.
     *
     * @param environmentId
     *  The ID of the environment to lookup
     *
     * @throws BadRequestException
     *  if no environmentId is provided
     *
     * @throws NotFoundException
     *  if the given ID cannot be resolved to a valid Environment
     *
     * @return
     *  the environment with the given ID
     */
    private Environment lookupEnvironment(String environmentId) {
        if (environmentId == null || environmentId.isEmpty()) {
            throw new BadRequestException("No environment specified");
        }

        Environment environment = this.envCurator.get(environmentId);
        if (environment == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", environmentId));
        }

        return environment;
    }

    @Override
    @Transactional
    public EnvironmentDTO getEnvironment(@Verify(Environment.class) String envId) {
        Environment environment = this.lookupEnvironment(envId);

        return translator.translate(environment, EnvironmentDTO.class);
    }

    @Override
    @Transactional
    public EnvironmentDTO updateEnvironment(@Verify(Environment.class) String envId, EnvironmentDTO dto) {
        if (dto == null) {
            throw new BadRequestException(this.i18n.tr("no environment update data provided"));
        }

        Environment environment = this.lookupEnvironment(envId);

        // Impl note:
        // Environment ID, owner, and environment content are not updateable through this endpoint
        // All validations must be performed first or we risk Hibernate doing a partial update if we
        // lose control of the transaction boundaries.
        String name = dto.getName();
        if (name != null) {
            if (name.isBlank()) {
                throw new BadRequestException(this.i18n.tr("environment name must be a non-empty value"));
            }

            if (name.length() > Environment.NAME_MAX_LENGTH) {
                throw new BadRequestException(this.i18n.tr("environment name cannot exceed {0} characters",
                    Environment.NAME_MAX_LENGTH));
            }
        }

        String type = dto.getType();
        if (type != null) {
            if (type.length() > Environment.TYPE_MAX_LENGTH) {
                throw new BadRequestException(this.i18n.tr("environment type cannot exceed {0} characters",
                    Environment.TYPE_MAX_LENGTH));
            }
        }

        String description = dto.getDescription();
        if (description != null) {
            if (description.length() > Environment.DESCRIPTION_MAX_LENGTH) {
                String errmsg = this.i18n.tr("environment description cannot exceed {0} characters",
                    Environment.DESCRIPTION_MAX_LENGTH);

                throw new BadRequestException(errmsg);
            }
        }

        String contentPrefix = dto.getContentPrefix();
        if (contentPrefix != null) {
            if (contentPrefix.length() > Environment.CONTENT_PREFIX_MAX_LENGTH) {
                String errmsg = this.i18n.tr("environment content prefix cannot exceed {0} characters",
                    Environment.CONTENT_PREFIX_MAX_LENGTH);

                throw new BadRequestException(errmsg);
            }
        }

        // Impl note:
        // Because of Hibernate's magic store-on-commit stuff, we have to do these as a separate set
        // of checks to retain atomicity, or we could find ourselves in an undefined state.
        if (name != null) {
            environment.setName(name);
        }

        if (type != null) {
            environment.setType(!type.isBlank() ? type : null);
        }

        if (description != null) {
            environment.setDescription(!description.isBlank() ? description : null);
        }

        if (contentPrefix != null) {
            environment.setContentPrefix(!contentPrefix.isBlank() ? contentPrefix : null);
        }

        environment = this.envCurator.merge(environment);
        this.envCurator.flush();

        return translator.translate(environment, EnvironmentDTO.class);
    }

    @Override
    @Transactional
    public void deleteEnvironment(@Verify(Environment.class) String envId, Boolean retainConsumers) {
        Environment environment = this.lookupEnvironment(envId);

        List<Consumer> consumers = this.envCurator.getEnvironmentConsumers(environment);

        // Impl note:
        // Original behavior was to always delete consumers when their last environment is deleted.
        // The update adds the optional behavior to retain consumers in such a case, but to maintain
        // backwards compatibility, we default to deleting consumers.
        if (retainConsumers == null || !retainConsumers) {
            Map<Boolean, List<Consumer>> partitionedConsumers = consumers.stream()
                .collect(Collectors.partitioningBy(consumer -> consumer.getEnvironmentIds().size() == 1));

            deleteConsumers(environment, partitionedConsumers.get(true));
            removeConsumersFromEnvironment(environment, partitionedConsumers.get(false));
        }
        else {
            removeConsumersFromEnvironment(environment, consumers);
        }

        log.info("Deleting environment: {}", environment);
        this.envCurator.delete(environment);
    }

    private void removeConsumersFromEnvironment(Environment environment, List<Consumer> consumers) {
        if (consumers.isEmpty()) {
            log.info("No consumers found in environment: {}", environment);
            return;
        }

        // No need to delete env from consumers manually. It will be handled by cascading delete.
        List<String> consumerIds = consumers.stream()
            .map(Consumer::getId)
            .toList();

        Set<String> entitlementsToBeRegenerated = this.entitlementEnvironmentFilter
            .filterEntitlements(prepareEnvironmentUpdates(environment, consumerIds));
        this.entCertService.regenerateCertificatesByEntitlementIds(entitlementsToBeRegenerated, true);

        for (Consumer consumer : consumers) {
            this.contentAccessManager.removeContentAccessCert(consumer);
        }
    }

    private EnvironmentUpdates prepareEnvironmentUpdates(Environment environment, List<String> consumerIds) {
        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        Map<String, List<String>> envsByConsumerId = this.envCurator.findEnvironmentsOf(consumerIds);
        for (Map.Entry<String, List<String>> consumerEnvs : envsByConsumerId.entrySet()) {
            List<String> updatedEnvs = consumerEnvs.getValue().stream()
                .filter(s -> !environment.getId().equals(s))
                .toList();
            environmentUpdate.put(consumerEnvs.getKey(), consumerEnvs.getValue(), updatedEnvs);
        }
        return environmentUpdate;
    }

    private void deleteConsumers(Environment environment, List<Consumer> consumers) {
        if (consumers.isEmpty()) {
            log.info("No consumers found for deletion with environment: {}", environment);
            return;
        }

        // Cleanup all consumers and their entitlements:
        log.info("Deleting consumers in environment {}", environment);

        List<Long> serialsToRevoke = new ArrayList<>(consumers.size());
        List<String> idCertsToDelete = new ArrayList<>(consumers.size());
        List<String> caCertsToDelete = new ArrayList<>(consumers.size());

        for (Consumer consumer : consumers) {
            log.info("Deleting consumer: {}", consumer);

            IdentityCertificate idCert = consumer.getIdCert();
            if (idCert != null) {
                idCertsToDelete.add(idCert.getId());
                serialsToRevoke.add(idCert.getSerial().getId());
            }
            SCACertificate contentAccessCert = consumer.getContentAccessCert();
            if (contentAccessCert != null) {
                caCertsToDelete.add(contentAccessCert.getId());
                serialsToRevoke.add(contentAccessCert.getSerial().getId());
            }

            // We're about to delete these consumers; no need to regen/dirty their dependent
            // entitlements or recalculate status.
            this.poolService.revokeAllEntitlements(consumer, false);
            this.consumerCurator.delete(consumer);
        }

        int deletedCerts = this.identityCertificateCurator.deleteByIds(idCertsToDelete);
        log.debug("Deleted {} identity certificates", deletedCerts);

        int deletedCerts1 = this.contentAccessCertificateCurator.deleteByIds(caCertsToDelete);
        log.debug("Deleted {} content access certificates", deletedCerts1);

        int revokedSerials = this.certificateSerialCurator.revokeByIds(serialsToRevoke);
        log.debug("Revoked {} certificate serials", revokedSerials);
    }

    @Override
    @Transactional
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
        }
        catch (PersistenceException pe) {
            if (rdbmsExceptionTranslator.isConstraintViolationDuplicateEntry(pe)) {
                log.info("Concurrent content promotion will cause this request to fail.", pe);

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
    @Transactional
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

        // Set the environment's last content update time so we can regenerate SCA content payloads for
        // consumers of this environment
        environment.syncLastContentUpdate();

        // Impl note: Unfortunately, we have to make an additional set here, as the keySet isn't
        // serializable. Attempting to use it causes exceptions.
        Set<String> demotedContentIds = new HashSet<>(demotedContent.keySet());
        return regenCertificates(environment, demotedContentIds, lazyRegen);
    }

    @Override
    @Transactional
    @SecurityHole(activationKey = true)
    public ConsumerDTO createConsumerInEnvironment(String envId, ConsumerDTO consumer,
        String userName, String activationKeys) throws BadRequestException {

        this.dtoValidator.validateCollectionElementsNotNull(consumer::getInstalledProducts,
            consumer::getGuestIds, consumer::getCapabilities);

        List<EnvironmentDTO> environmentDTOs = Arrays.stream(envId.trim().split("\\s*,\\s*"))
            .map(this::lookupEnvironment)
            .map(this.translator.getStreamMapper(Environment.class, EnvironmentDTO.class))
            .toList();

        // Check if all envs belongs to same org
        BinaryOperator<String> unify = (prev, next) -> {
            if (prev != null && !prev.equals(next)) {
                throw new BadRequestException(
                    this.i18n.tr("Two or more environments belong to different organizations"));
            }

            return next;
        };

        String ownerKey = environmentDTOs.stream()
            .map(env -> env.getOwner().getKey())
            .reduce(null, unify);

        consumer.setEnvironments(environmentDTOs);
        return this.consumerResource.createConsumer(consumer, userName, ownerKey, activationKeys, true);
    }

    @Override
    @Transactional
    public Stream<ContentOverrideDTO> getEnvironmentContentOverrides(
        @Verify(Environment.class) String environmentId) {

        Environment environment = this.lookupEnvironment(environmentId);

        return this.envContentOverrideCurator.getList(environment)
            .stream()
            .map(this.translator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class));
    }

    @Override
    @Transactional
    public Stream<ContentOverrideDTO> putEnvironmentContentOverrides(
        @Verify(Environment.class) String environmentId,
        List<ContentOverrideDTO> contentOverrideDTOs) {

        this.contentOverrideValidator.validate(contentOverrideDTOs);
        Environment environment = this.lookupEnvironment(environmentId);

        List<EnvironmentContentOverride> overrides = contentOverrideDTOs.stream()
            .map(dto -> new EnvironmentContentOverride()
                .setEnvironment(environment)
                .setContentLabel(dto.getContentLabel())
                .setName(dto.getName())
                .setValue(dto.getValue()))
            .toList();

        Map<String, Map<String, EnvironmentContentOverride>> overrideMap = this.envContentOverrideCurator
            .retrieveAll(environment, overrides);

        try {
            for (EnvironmentContentOverride inbound : overrides) {
                EnvironmentContentOverride existing = overrideMap
                    .getOrDefault(inbound.getContentLabel(), Map.of())
                    .get(inbound.getName());

                if (existing != null) {
                    existing.setValue(inbound.getValue());
                    this.envContentOverrideCurator.merge(existing);
                }
                else {
                    EnvironmentContentOverride created = this.envContentOverrideCurator.create(inbound);

                    // Add the created override to the map so that additional overrides with the same content
                    // label and name but different value will be updated.
                    Map<String, EnvironmentContentOverride> nameToOverride = overrideMap
                        .getOrDefault(created.getContentLabel(), new HashMap<>());
                    nameToOverride.put(created.getName(), created);
                    overrideMap.put(created.getContentLabel(), nameToOverride);
                }
            }
        }
        catch (RuntimeException e) {
            // Make sure we clear all pending changes, since we don't want to risk storing only a
            // portion of the changes.
            this.envContentOverrideCurator.clear();

            // Re-throw the exception
            throw e;
        }

        environment.setUpdated(new Date());
        this.envContentOverrideCurator.flush();

        // Hibernate typically persists automatically before executing a query against a table with
        // pending changes, but if it doesn't, we can add a flush here to make sure this outputs the
        // correct values

        return this.envContentOverrideCurator.getList(environment)
            .stream()
            .map(this.translator.getStreamMapper(ContentOverride.class,
                ContentOverrideDTO.class));
    }

    @Override
    @Transactional
    public Stream<ContentOverrideDTO> deleteEnvironmentContentOverrides(
        @Verify(Environment.class) String environmentId,
        List<ContentOverrideDTO> contentOverrideDTOs) {

        Environment environment = this.lookupEnvironment(environmentId);

        if (contentOverrideDTOs == null || contentOverrideDTOs.isEmpty()) {
            this.envContentOverrideCurator.removeByParent(environment);
        }
        else {
            for (ContentOverrideDTO dto : contentOverrideDTOs) {
                if (dto == null) {
                    continue;
                }

                String label = dto.getContentLabel();

                if (label == null || label.isBlank()) {
                    // Why in god's name is this a standard feature of content override deletions!?
                    this.envContentOverrideCurator.removeByParent(environment);
                }
                else {
                    String name = dto.getName();
                    if (name == null || name.isBlank()) {
                        this.envContentOverrideCurator.removeByContentLabel(environment, label);
                    }
                    else {
                        this.envContentOverrideCurator.removeByName(environment, label, name);
                    }
                }
            }
        }

        environment.setUpdated(new Date());
        this.envContentOverrideCurator.flush();

        return this.envContentOverrideCurator.getList(environment)
            .stream()
            .map(this.translator.getStreamMapper(ContentOverride.class, ContentOverrideDTO.class));
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

        String namespace = environment.getOwner().getKey();

        Content resolved = this.contentCurator.resolveContentId(namespace, contentId);
        if (resolved == null) {
            throw new NotFoundException(i18n.tr("Unable to find a content with the ID \"{0}\"", contentId));
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
     *
     * @param contentToPromote
     * @param env
     * @return contentIds Ids of the promoted content
     */
    private Set<String> batchCreate(List<ContentToPromoteDTO> contentToPromote, Environment env) {
        Map<String, EnvironmentContent> resolved = new HashMap<>();

        for (ContentToPromoteDTO prequest : contentToPromote) {
            // TODO: This could probably be done in bulk to improve response times in multi-content
            // requests
            Content content = this.resolveContent(env, prequest.getContentId());

            EnvironmentContent envcontent = new EnvironmentContent()
                .setEnvironment(env)
                .setContent(content)
                .setEnabled(prequest.getEnabled());

            resolved.put(content.getId(), envcontent);
        }

        // If we made it here, all the content resolved properly; update our environment and persist
        // the changes
        for (EnvironmentContent envcontent : resolved.values()) {
            env.addEnvironmentContent(envcontent);
        }

        // Set the environment's last content update time so we can regenerate SCA content payloads for
        // consumers of this environment
        env.syncLastContentUpdate();

        this.envCurator.merge(env);

        // Return the final set of content IDs that were promoted
        return resolved.keySet();
    }
}
