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
package org.candlepin.async.tasks;

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.IdentityCertificateCurator;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
    public static final String CFG_BATCH_SIZE = "batch_size";
    public static final String DEFAULT_BATCH_SIZE = "1000";

    private final Configuration config;
    private final ConsumerCurator consumerCurator;
    private final DeletedConsumerCurator deletedConsumerCurator;
    private final IdentityCertificateCurator identityCertificateCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final CertificateSerialCurator certificateSerialCurator;

    @Inject
    public InactiveConsumerCleanerJob(Configuration config,
        ConsumerCurator consumerCurator,
        DeletedConsumerCurator deletedConsumerCurator,
        IdentityCertificateCurator identityCertificateCurator,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        CertificateSerialCurator certificateSerialCurator) {
        this.config = Objects.requireNonNull(config);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.deletedConsumerCurator = Objects.requireNonNull(deletedConsumerCurator);
        this.identityCertificateCurator = Objects.requireNonNull(identityCertificateCurator);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.certificateSerialCurator = Objects.requireNonNull(certificateSerialCurator);
    }

    /**
     * Deletes inactive consumers and creates {@link DeletedConsumer} records.
     * The identity certificate and content access certificate for the consumers
     * are removed and their serials are revoked.
     *
     * @param inactiveConsumerIds - ids of inactive consumers to delete.
     * @return the number of consumers that have been deleted.
     */
    @Transactional
    public int deleteInactiveConsumers(Collection<String> inactiveConsumerIds) {
        if (inactiveConsumerIds == null || inactiveConsumerIds.isEmpty()) {
            return 0;
        }

        deletedConsumerCurator.createDeletedConsumers(inactiveConsumerIds);

        // Retrieve the certs and their serials for the inactive consumers.
        List<String> idCertsToRemove = consumerCurator
            .getIdentityCertIds(inactiveConsumerIds);
        List<String> scaCertsToRemove = consumerCurator
            .getContentAccessCertIds(inactiveConsumerIds);
        List<Long> serialIdsToRevoke = consumerCurator
            .getSerialIdsForCerts(scaCertsToRemove, idCertsToRemove);

        int deletedConsumers = consumerCurator
            .deleteConsumers(inactiveConsumerIds);

        // Delete the certificates and revoke their serials.
        identityCertificateCurator.deleteByIds(idCertsToRemove);
        contentAccessCertificateCurator.deleteByIds(scaCertsToRemove);
        certificateSerialCurator.revokeByIds(serialIdsToRevoke);

        return deletedConsumers;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Instant lastCheckedInRetention = getRetentionDate(CFG_LAST_CHECKED_IN_RETENTION_IN_DAYS);
        Instant nonCheckedInRetention = getRetentionDate(CFG_LAST_UPDATED_IN_RETENTION_IN_DAYS);

        List<String> inactiveConsumerIds = consumerCurator
            .getInactiveConsumerIds(lastCheckedInRetention, nonCheckedInRetention);

        int deletedCount = 0;
        int batchSize = getBatchSize();
        for (List<String> batch : Iterables.partition(inactiveConsumerIds, batchSize)) {
            log.debug("Cleaning inactive consumers with a batch of ids: {}", batch);
            deletedCount += deleteInactiveConsumers(batch);
        }

        log.info("InactiveConsumerCleanerJob has run! {} consumers removed.", deletedCount);
        context.setJobResult(JOB_NAME + " completed successfully. %d consumers removed.", deletedCount);
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
