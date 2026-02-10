/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.async.tasks;

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.InactiveConsumerRecord;
import org.candlepin.util.Transactional;

import com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.inject.Inject;



/**
 * If enabled, the InactiveConsumerCleanerJob periodically runs and removes inactive consumers
 * based on their last checked in date and last updated dated. The identity certificate and
 * content access certificate are removed and their serials are revoked.
 */
public class InactiveConsumerCleanerJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(InactiveConsumerCleanerJob.class);

    public static final String JOB_KEY = "InactiveConsumerCleanerJob";
    public static final String JOB_NAME = "Inactive Consumer Cleaner";

    public static final String CFG_LAST_CHECKED_IN_RETENTION_IN_DAYS = "last_checked_in_retention_in_days";
    public static final int DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS = 397;

    public static final String CFG_LAST_UPDATED_IN_RETENTION_IN_DAYS = "last_updated_retention_in_days";
    public static final int DEFAULT_LAST_UPDATED_IN_RETENTION_IN_DAYS = 30;

    public static final String CFG_ANON_CLOUD_CONSUMER_RETENTION = "anon_cloud_consumer_retention";
    public static final int DEFAULT_ANON_CLOUD_CONSUMER_RETENTION = 15; // In days

    public static final String CFG_BATCH_SIZE = "batch_size";

    // Any higher than 1k runs the risk of hitting memory/heap limits and crashing with an OOM
    public static final int DEFAULT_BATCH_SIZE = 1000;

    private final Configuration config;
    private final ConsumerCurator consumerCurator;
    private final AnonymousCloudConsumerCurator anonymousCloudConsumerCurator;
    private final IdentityCertificateCurator identityCertificateCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final CertificateSerialCurator certificateSerialCurator;

    private final EventFactory eventFactory;
    private final EventSink eventSink;

    @Inject
    public InactiveConsumerCleanerJob(Configuration config,
        ConsumerCurator consumerCurator,
        AnonymousCloudConsumerCurator anonymousCloudConsumerCurator,
        IdentityCertificateCurator identityCertificateCurator,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        CertificateSerialCurator certificateSerialCurator,
        EventSink eventSink,
        EventFactory eventFactory) {

        this.config = Objects.requireNonNull(config);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.anonymousCloudConsumerCurator = Objects.requireNonNull(anonymousCloudConsumerCurator);
        this.identityCertificateCurator = Objects.requireNonNull(identityCertificateCurator);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.certificateSerialCurator = Objects.requireNonNull(certificateSerialCurator);

        this.eventSink = Objects.requireNonNull(eventSink);
        this.eventFactory = Objects.requireNonNull(eventFactory);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Instant lastCheckedInRetention = this.getRetentionDate(CFG_LAST_CHECKED_IN_RETENTION_IN_DAYS);
        Instant nonCheckedInRetention = this.getRetentionDate(CFG_LAST_UPDATED_IN_RETENTION_IN_DAYS);
        int batchSize = this.getBatchSize();

        int deletedConsumers =
            this.deleteInactiveConsumers(lastCheckedInRetention, nonCheckedInRetention, batchSize);

        Instant anonymousConsumerRetention = this.getRetentionDate(CFG_ANON_CLOUD_CONSUMER_RETENTION);
        int deletedAnonymousCloudConsumers =
            this.deleteInactiveAnonymousCloudConsumers(anonymousConsumerRetention, batchSize);

        String result = String.format(
            "%s complete; %d consumers removed and %d anonymous cloud consumers removed",
            JOB_NAME, deletedConsumers, deletedAnonymousCloudConsumers);

        log.info(result);
        context.setJobResult(result);
    }

    private int deleteInactiveConsumers(Instant lastCheckedInRetention, Instant nonCheckedInRetention,
        int batchSize) {

        log.info("Fetching inactive consumers using retention dates: last checkin: {}, last update: {}",
            lastCheckedInRetention, nonCheckedInRetention);

        List<InactiveConsumerRecord> inactiveConsumers = this.consumerCurator
            .getInactiveConsumers(lastCheckedInRetention, nonCheckedInRetention);

        return deleteInBatches(inactiveConsumers, batchSize, "inactive consumers",
            this::deleteInactiveConsumers);
    }

    /**
     * Deletes inactive consumers and creates {@link DeletedConsumer} records.
     * The identity certificate and content access certificate for the consumers
     * are removed and their serials are revoked.
     *
     * @param args
     *  Arguments for this method; abstracted out due to restrictions in the transactional framework. Must
     *  consist of a single element which is a collection of InactiveConsumerRecord instances.
     *
     * @return
     *  the number of consumers that have been deleted.
     */
    private Integer deleteInactiveConsumers(Object... args) {
        if (args == null || args.length < 1) {
            log.trace("No arguments sent to transactional operation");
            return 0;
        }

        Collection<InactiveConsumerRecord> inactiveConsumers = (Collection<InactiveConsumerRecord>) args[0];
        if (inactiveConsumers == null || inactiveConsumers.isEmpty()) {
            return 0;
        }

        // This probably isn't the most efficient way to go about this. Once the stream gather operation is
        // available, we can probably inline all of the partitioning and this into one lazy operation rather
        // than doing this.
        Map<String, List<String>> orgConsumerMap = new HashMap<>();
        List<String> consumerIds = new ArrayList<>();
        Map<String, Boolean> orgAnonymousMap = new HashMap<>();

        for (InactiveConsumerRecord rec : inactiveConsumers) {
            consumerIds.add(rec.consumerId());
            orgConsumerMap.computeIfAbsent(rec.ownerKey(), key -> new ArrayList<>())
                .add(rec.consumerUuid());

            orgAnonymousMap.computeIfAbsent(rec.ownerKey(), key -> {
                return rec.isOwnerAnonymous() == null ? false : rec.isOwnerAnonymous();
            });
        }

        // Retrieve the certs and their serials for the inactive consumers.
        List<String> idCertsToRemove = consumerCurator.getIdentityCertIds(consumerIds);
        List<String> scaCertsToRemove = consumerCurator.getContentAccessCertIds(consumerIds);
        List<Long> serialIdsToRevoke = this.consumerCurator.getConsumerCertSerialIds(consumerIds);

        int deletedConsumers = this.consumerCurator.deleteConsumers(consumerIds);

        // Delete the certificates and revoke their serials.
        this.identityCertificateCurator.deleteByIds(idCertsToRemove);
        this.contentAccessCertificateCurator.deleteByIds(scaCertsToRemove);
        this.certificateSerialCurator.revokeByIds(serialIdsToRevoke);

        // Build and queue bulk-deletion events on a per-org basis :(
        for (Map.Entry<String, List<String>> entry : orgConsumerMap.entrySet()) {
            Boolean anonymous = orgAnonymousMap.get(entry.getKey());
            Event event = this.eventFactory.bulkConsumerDeletion(entry.getKey(), anonymous, entry.getValue());
            this.eventSink.queueEvent(event);
        }

        return deletedConsumers;
    }

    private int deleteInactiveAnonymousCloudConsumers(Instant retention, int batchSize) {
        List<String> ids = this.anonymousCloudConsumerCurator.getInactiveAnonymousCloudConsumerIds(retention);

        return deleteInBatches(ids, batchSize, "inactive anonymous cloud consumers",
            this.anonymousCloudConsumerCurator::deleteAnonymousCloudConsumers);
    }

    private <T> int deleteInBatches(List<T> items, int batchSize, String entityDescription,
        Function<List<T>, Integer> deletionFunction) {

        log.info("Found {} {}", items.size(), entityDescription);
        if (items.isEmpty()) {
            return 0;
        }

        Transactional transaction = new Transactional(this.consumerCurator.getEntityManager())
            .onCommit(status -> this.eventSink.sendEvents())
            .onRollback(status -> this.eventSink.rollback());

        int totalBatches = (items.size() / batchSize) + Math.min(items.size() % batchSize, 1);
        int batchCount = 0;
        int deletedCount = 0;
        for (List<T> batch : Iterables.partition(items, batchSize)) {
            log.info("Deleting {} {} (batch {} of {})",
                batch.size(), entityDescription, ++batchCount, totalBatches);
            deletedCount += transaction.execute(() -> deletionFunction.apply(batch));
        }

        return deletedCount;
    }

    /**
     * Retrieves the retention instant based on the provided configuration name and default value.
     *
     * @param configurationName - name of the configuration to retrieve.
     * @return the retention instant based on the provided configuration name and default value.
     * @throws JobExecutionException when there is an invalid retention configuration.
     */
    private Instant getRetentionDate(String configurationName) throws JobExecutionException {
        String configuration = ConfigProperties.jobConfig(JOB_KEY, configurationName);
        int retentionDays = this.config.getInt(configuration);
        if (retentionDays > 0) {
            return Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        }
        else {
            String errorMessage = String.format(
                "Invalid value for configuration \"%s\", must be a positive integer: %s", configuration,
                retentionDays);

            log.error(errorMessage);
            throw new JobExecutionException(errorMessage, true);
        }
    }

    /**
     * Retrieves the batch size for removing inactive consumers.
     *
     * @return the batch size based on the configuration and default value.
     * @throws JobExecutionException when there is an invalid batch size configuration.
     */
    private int getBatchSize() throws JobExecutionException {
        String configuration = ConfigProperties.jobConfig(JOB_KEY, CFG_BATCH_SIZE);
        int batchSize = this.config.getInt(configuration);
        if (batchSize <= 0) {
            String errorMessage = String.format(
                "Invalid value for configuration \"%s\", must be a positive integer: %s", configuration,
                batchSize);

            log.error(errorMessage);
            throw new JobExecutionException(errorMessage, true);
        }

        return batchSize;
    }
}
