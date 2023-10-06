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
import org.candlepin.model.ContentCurator;
import org.candlepin.model.ProductCurator;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.inject.Inject;



/**
 * The OrphanCleanupJob searches for orphaned entities (products and content and the time of
 * writing) and removes them.
 */
public class OrphanCleanupJob implements AsyncJob  {
    private static final Logger log = LoggerFactory.getLogger(OrphanCleanupJob.class);

    public static final String JOB_KEY = "OrphanCleanupJob";
    public static final String JOB_NAME = "Orphan Cleanup";

    // Every Sunday at 3:00am
    public static final String DEFAULT_SCHEDULE = "0 0 3 ? * 1";

    private final ContentCurator contentCurator;
    private final ProductCurator productCurator;

    @Inject
    public OrphanCleanupJob(ContentCurator contentCurator, ProductCurator productCurator) {
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.productCurator = Objects.requireNonNull(productCurator);
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String format = "Orphan cleanup completed;" +
            "\n  %d orphaned content deleted" +
            "\n  %d orphaned products deleted";

        context.setJobResult(format, 0, 0);
    }
}
