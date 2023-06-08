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
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ExpiredCertificate;
import org.candlepin.model.IdentityCertificateCurator;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;


/**
 * The job will periodically delete all expired identity and content certificates
 * as well as all revoked and expired certificate serials.
 */
public class CertificateCleanupJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(CertificateCleanupJob.class);

    public static final String JOB_KEY = "CertificateCleanupJob";
    public static final String JOB_NAME = "Certificate Cleanup";
    // Every noon
    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    private final ConsumerCurator consumerCurator;
    private final IdentityCertificateCurator identCertCurator;
    private final ContentAccessCertificateCurator caCertCurator;
    private final CertificateSerialCurator serialCurator;

    @Inject
    public CertificateCleanupJob(
        ConsumerCurator consumers,
        IdentityCertificateCurator identCerts,
        ContentAccessCertificateCurator contentAccessCerts,
        CertificateSerialCurator serialCurator) {
        this.consumerCurator = Objects.requireNonNull(consumers);
        this.identCertCurator = Objects.requireNonNull(identCerts);
        this.caCertCurator = Objects.requireNonNull(contentAccessCerts);
        this.serialCurator = Objects.requireNonNull(serialCurator);
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Starting certificate cleanup.");
        // Skipping entitlement certificates as those are handled by ExpiredPoolsCleanupJob
        cleanupExpiredIdentityCerts();
        cleanupExpiredContentAccessCerts();
        cleanupCertificateSerials();
        log.debug("Certificate cleanup successfully completed.");
    }

    private void cleanupExpiredIdentityCerts() {
        List<ExpiredCertificate> allExpiredIdCertificates = this.identCertCurator.listAllExpired();

        if (allExpiredIdCertificates == null || allExpiredIdCertificates.isEmpty()) {
            log.info("No expired identity certificates to clean up.");
            return;
        }
        else {
            log.info("Cleaning up {} expired identity certificates.", allExpiredIdCertificates.size());
        }

        List<String> expiredCertIds = certIdsOf(allExpiredIdCertificates);
        int unlinkedConsumers = this.consumerCurator.unlinkIdCertificates(expiredCertIds);
        log.debug("Unlinked identity certificates of {} consumers.", unlinkedConsumers);

        int certsDeleted = this.identCertCurator.deleteByIds(expiredCertIds);
        log.debug("Deleted {} identity certificates.", certsDeleted);

        List<Long> expiredSerials = serialsOf(allExpiredIdCertificates);
        int revokedSerials = this.serialCurator.revokeByIds(expiredSerials);
        log.debug("Revoked {} identity certificate serials.", revokedSerials);
    }

    private void cleanupExpiredContentAccessCerts() {
        List<ExpiredCertificate> allExpiredCaCertificates = this.caCertCurator.listAllExpired();

        if (allExpiredCaCertificates == null || allExpiredCaCertificates.isEmpty()) {
            log.info("No expired content access certificates to clean up.");
            return;
        }
        else {
            log.info("Cleaning up {} expired content access certificates.", allExpiredCaCertificates.size());
        }

        List<String> expiredCertIds = certIdsOf(allExpiredCaCertificates);
        int unlinkedConsumers = this.consumerCurator.unlinkCaCertificates(expiredCertIds);
        log.debug("Unlinked content access certificates of {} consumers.", unlinkedConsumers);

        int certsDeleted = this.caCertCurator.deleteByIds(expiredCertIds);
        log.debug("Deleted {} content access certificates.", certsDeleted);

        List<Long> expiredSerials = serialsOf(allExpiredCaCertificates);
        int revokedSerials = this.serialCurator.revokeByIds(expiredSerials);
        log.debug("Revoked {} content access certificate serials.", revokedSerials);
    }

    private void cleanupCertificateSerials() {
        int deleted = this.serialCurator.deleteRevokedExpiredSerials();
        log.debug("Cleaning up {} expired and revoked certificate serials.", deleted);
    }

    private List<String> certIdsOf(List<ExpiredCertificate> expiredCertificates) {
        return expiredCertificates.stream()
            .map(ExpiredCertificate::getCertId)
            .collect(Collectors.toList());
    }

    private List<Long> serialsOf(List<ExpiredCertificate> expiredCertificates) {
        return expiredCertificates.stream()
            .map(ExpiredCertificate::getSerial)
            .collect(Collectors.toList());
    }

}
