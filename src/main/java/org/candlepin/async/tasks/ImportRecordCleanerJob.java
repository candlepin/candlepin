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
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/**
 * Deletes all but the newest N records, defined by the num_of_records_to_keep/DEFAULT_KEEP variable,
 * for each owner.
 */
public class ImportRecordCleanerJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(ImportRecordCleanerJob.class);

    public static final String JOB_KEY = "ImportRecordCleanerJob";
    public static final String JOB_NAME = "Import Record Cleaner";

    // Every noon
    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    public static final String CFG_KEEP = "num_of_records_to_keep";
    public static final String DEFAULT_KEEP = "10";

    private final OwnerCurator ownerCurator;
    private final ImportRecordCurator importRecordCurator;
    private final Configuration config;

    @Inject
    public ImportRecordCleanerJob(ImportRecordCurator importRecordCurator,
        OwnerCurator ownerCurator, Configuration config) {
        this.importRecordCurator = Objects.requireNonNull(importRecordCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        int toKeep = this.config.getInt(ConfigProperties.jobConfig(JOB_KEY, CFG_KEEP));

        if (toKeep < 0) {
            String errmsg = String.format(
                "Invalid value for number of records to keep, must be 0 or a positive integer: %s",
                toKeep);

            log.error(errmsg);
            throw new JobExecutionException(errmsg, true);
        }

        int deleted = 0;
        for (Owner owner : this.ownerCurator.listAll()) {
            List<ImportRecord> records = this.importRecordCurator.findRecords(owner);

            if (toKeep < records.size()) {
                // records are already sorted by date, so just shave off of the end
                this.importRecordCurator.bulkDelete(records.subList(toKeep, records.size()));
                deleted += records.size() - toKeep;
            }
        }

        String outcome;

        if (deleted > 0) {
            outcome = String.format("Deleted %d import records.", deleted);
            log.info(outcome);
        }
        else {
            outcome = "No import records were deleted.";
            log.debug(outcome);
        }

        context.setJobResult(outcome);
    }
}
