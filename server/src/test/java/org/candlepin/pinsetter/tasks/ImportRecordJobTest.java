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

import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import javax.inject.Inject;

/**
 *
 */
public class ImportRecordJobTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ImportRecordCurator importRecordCurator;
    @Inject private ImportRecordJob job;

    @Test
    public void noRecords() throws Exception {
        Owner owner = new Owner("owner");
        Owner another = new Owner("another_owner");
        ownerCurator.create(owner);
        ownerCurator.create(another);

        // just make sure that running this on owners with no records
        // doesn't blow up
        this.job.execute(null);
    }

    @Test
    public void singleOwner() throws Exception {
        Owner owner = new Owner("owner");
        ownerCurator.create(owner);

        for (int i = 0; i < 13; i++) {
            ImportRecord record = new ImportRecord(owner);
            record.recordStatus(ImportRecord.Status.SUCCESS, "great!");

            this.importRecordCurator.create(record);
        }

        this.job.execute(null);

        List<ImportRecord> records = this.importRecordCurator.findRecords(owner).list();

        Assert.assertEquals(10, records.size());
    }

    @Test
    public void lessThanThreshold() throws Exception {
        Owner owner = new Owner("owner");
        ownerCurator.create(owner);

        for (int i = 0; i < 7; i++) {
            ImportRecord record = new ImportRecord(owner);
            record.recordStatus(ImportRecord.Status.SUCCESS, "great!");

            this.importRecordCurator.create(record);
        }

        this.job.execute(null);

        List<ImportRecord> records = this.importRecordCurator.findRecords(owner).list();

        Assert.assertEquals(7, records.size());
    }

    @Test
    public void multipleOwners() throws Exception {
        Owner owner1 = new Owner("owner1");
        Owner owner2 = new Owner("owner2");
        ownerCurator.create(owner1);
        ownerCurator.create(owner2);

        for (int i = 0; i < 23; i++) {
            ImportRecord record = new ImportRecord(owner1);
            record.recordStatus(ImportRecord.Status.FAILURE, "Bad bad");

            this.importRecordCurator.create(record);
        }

        for (int i = 0; i < 4; i++) {
            ImportRecord record = new ImportRecord(owner2);
            record.recordStatus(ImportRecord.Status.SUCCESS, "Excellent");

            this.importRecordCurator.create(record);
        }

        this.job.execute(null);

        Assert.assertEquals(10, importRecordCurator.findRecords(owner1).list().size());
        Assert.assertEquals(4, importRecordCurator.findRecords(owner2).list().size());
    }

}
