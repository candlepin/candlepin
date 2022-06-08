/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import org.candlepin.model.CertificateCurator;
import org.candlepin.model.ConsumerCurator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * The job will periodically delete all expired identity and content certificates
 * as well as all revoked and expired certificate serials.
 */
public class CertificateCleanupJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(CertificateCleanupJob.class);

    public static final String JOB_KEY = "CertificateCleanupJob";
    public static final String JOB_NAME = "Certificate Cleanup";
    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    private final CertificateCurator certificateCurator;

    @Inject
    public CertificateCleanupJob(CertificateCurator certificateCurator) {
        this.certificateCurator = Objects.requireNonNull(certificateCurator);
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Starting certificate cleanup");

        // Skipping entitlement certificates as those are handled by ExpiredPoolsCleanupJob
        int count = this.certificateCurator.deleteExpiredCertificates(Certificate.Type.IDENTITY,
            Certificate.Type.CONTENT_ACCESS);

        String result = String.format("Certificate cleanup successfully completed; %d expired certs removed",
            count);

        log.debug(result);
        context.setResult(result);
    }

}
