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
package org.candlepin.pinsetter.tasks;

import com.google.inject.Inject;

import java.util.List;

import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Deletes all but the oldest N records, defined by the
 * DEFAULT_KEEP variable.
 */
public class ImportRecordJob extends KingpinJob {

    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    // TODO:  Pull this in from the config?
    private static final int DEFAULT_KEEP = 10;

    private OwnerCurator ownerCurator;
    private ImportRecordCurator importRecordCurator;

    @Inject
    public ImportRecordJob(ImportRecordCurator importRecordCurator,
            OwnerCurator ownerCurator) {
        this.importRecordCurator = importRecordCurator;
        this.ownerCurator = ownerCurator;
    }

    // TODO:  This seems pretty slow... I'm sure there is a fancy JPA
    //        query that can do this, but I am a little hesitant to get
    //        really fancy with sub-selects as they are not supported by
    //        all databases.
    @Override
    public void toExecute(JobExecutionContext jec) throws JobExecutionException {
        for (Owner owner : this.ownerCurator.listAll()) {
            List<ImportRecord> records = this.importRecordCurator.findRecords(owner);

            if (DEFAULT_KEEP < records.size()) {
                // records are already sorted by date, so just shave off of the end
                this.importRecordCurator.bulkDelete(
                        records.subList(DEFAULT_KEEP, records.size()));
            }
        }
    }

}
