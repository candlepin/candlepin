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

import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Test suite for the ImportRecordCleanerJob class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ImportRecordCleanerJobTest extends DatabaseTestFixture {

    private ImportRecordCleanerJob createJobInstance() {
        return new ImportRecordCleanerJob(this.importRecordCurator, this.ownerCurator, this.config);
    }

    @Test
    public void noRecords() throws Exception {
        Owner owner = new Owner("owner");
        Owner another = new Owner("another_owner");
        ownerCurator.create(owner);
        ownerCurator.create(another);

        // just make sure that running this on owners with no records
        // doesn't blow up
        JobExecutionContext context = mock(JobExecutionContext.class);

        ImportRecordCleanerJob job = createJobInstance();
        String result = (String) job.execute(context);
        assertEquals("No import records were deleted.", result);
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

        JobExecutionContext context = mock(JobExecutionContext.class);
        ImportRecordCleanerJob job = createJobInstance();
        String result = (String) job.execute(context);

        List<ImportRecord> records = this.importRecordCurator.findRecords(owner).list();

        assertEquals(10, records.size());
        assertEquals("Deleted 3 import records.", result);
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

        JobExecutionContext context = mock(JobExecutionContext.class);
        ImportRecordCleanerJob job = createJobInstance();
        String result = (String) job.execute(context);

        List<ImportRecord> records = this.importRecordCurator.findRecords(owner).list();

        assertEquals(7, records.size());
        assertEquals("No import records were deleted.", result);
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

        JobExecutionContext context = mock(JobExecutionContext.class);
        ImportRecordCleanerJob job = createJobInstance();
        String result = (String) job.execute(context);

        assertEquals(10, importRecordCurator.findRecords(owner1).list().size());
        assertEquals(4, importRecordCurator.findRecords(owner2).list().size());
        assertEquals("Deleted 13 import records.", result);
    }

    private void setNumOfRecordsToKeep(int numOfRecordsToKeep) {
        String cfg = ConfigProperties.jobConfig(ImportRecordCleanerJob.JOB_KEY,
            ImportRecordCleanerJob.CFG_KEEP);

        this.config.setProperty(cfg, String.valueOf(numOfRecordsToKeep));
    }

    private static Stream<Arguments> numOfRecordsToKeepProvider() {
        return Stream.of(
            Arguments.of(ImportRecordCleanerJob.DEFAULT_KEEP),
            Arguments.of(60),
            Arguments.of(13),
            Arguments.of(1),
            Arguments.of(0));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, -50 })
    public void singleOwnerBadKeepConfig(int keep) throws Exception {
        this.setNumOfRecordsToKeep(keep);
        Owner owner = new Owner("owner");
        ownerCurator.create(owner);

        for (int i = 0; i < 3; i++) {
            ImportRecord record = new ImportRecord(owner);
            record.recordStatus(ImportRecord.Status.SUCCESS, "great!");
            this.importRecordCurator.create(record);
        }

        JobExecutionContext context = mock(JobExecutionContext.class);
        ImportRecordCleanerJob job = createJobInstance();
        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }

    @ParameterizedTest
    @MethodSource("numOfRecordsToKeepProvider")
    public void singleOwnerCustomConfigSettings(int keep) throws Exception {
        this.setNumOfRecordsToKeep(keep);
        Owner owner = new Owner("owner");
        ownerCurator.create(owner);

        int existingRecords = 13;
        for (int i = 0; i < existingRecords; i++) {
            ImportRecord record = new ImportRecord(owner);
            record.recordStatus(ImportRecord.Status.SUCCESS, "great!");
            this.importRecordCurator.create(record);
        }

        JobExecutionContext context = mock(JobExecutionContext.class);
        ImportRecordCleanerJob job = createJobInstance();
        String result = (String) job.execute(context);

        int expectedDeletedRecords = existingRecords - keep;
        int expectedKeptRecords = existingRecords > keep ? keep : existingRecords;

        assertEquals(expectedKeptRecords, importRecordCurator.findRecords(owner).list().size());
        if (expectedDeletedRecords > 0) {
            assertEquals("Deleted " + expectedDeletedRecords + " import records.", result);
        }
        else {
            assertEquals("No import records were deleted.", result);
        }
    }
}
