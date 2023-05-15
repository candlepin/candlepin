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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.Importer;
import org.candlepin.sync.ImporterException;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class ImportJobTest {
    @Mock private ManifestManager manifestManager;
    @Mock private OwnerCurator ownerCurator;

    private ImportJob buildImportJob() {
        return new ImportJob(this.ownerCurator, this.manifestManager);
    }

    private Owner createTestOwner(String key, String logLevel) {
        Owner owner = TestUtil.createOwner();

        owner.setId(TestUtil.randomString());
        owner.setKey(key);
        owner.setLogLevel(logLevel);

        return owner;
    }

    @Test
    public void testJobConfigSetOwner() {
        Owner owner = this.createTestOwner("owner_key", "log_level");

        JobConfig config = ImportJob.createJobConfig()
            .setOwner(owner);

        JobArguments args = config.getJobArguments();
        String argKey = args.keySet().iterator().next();

        assertNotNull(argKey);
        assertFalse(argKey.isEmpty());
        assertEquals(owner.getKey(), args.getAsString(argKey));

        // The context owner should also be set by this method
        assertEquals(owner, config.getContextOwner());
    }

    @Test
    public void testJobConfigSetUploadFileName() {
        String fileName = "file_name_1";

        JobConfig config = ImportJob.createJobConfig()
            .setUploadedFileName(fileName);

        JobArguments args = config.getJobArguments();
        String argKey = args.keySet().iterator().next();

        assertNotNull(argKey);
        assertFalse(argKey.isEmpty());
        assertEquals(fileName, args.getAsString(argKey));
    }

    @Test
    public void testJobConfigSetStoredFileId() {
        String storedFileId = "stored_file_id_1";

        JobConfig config = ImportJob.createJobConfig()
            .setStoredFileId(storedFileId);

        JobArguments args = config.getJobArguments();
        String argKey = args.keySet().iterator().next();

        assertNotNull(argKey);
        assertFalse(argKey.isEmpty());
        assertEquals(storedFileId, args.getAsString(argKey));
    }

    @Test
    public void testJobConfigSetConflictOverrides() {
        ConflictOverrides conflictOverrides = new ConflictOverrides(
            Importer.Conflict.DISTRIBUTOR_CONFLICT, Importer.Conflict.MANIFEST_SAME);

        JobConfig config = ImportJob.createJobConfig()
            .setConflictOverrides(conflictOverrides);

        JobArguments args = config.getJobArguments();

        assertNotNull(args);
        assertEquals(1, args.size());

        // We aren't concerned with the key it's stored under, just that it's stored. As such, we
        // need to get the key from the key set so we can reference it for use with getAsString.
        String argKey = args.keySet().iterator().next();

        assertNotNull(argKey);
        assertFalse(argKey.isEmpty());
        assertArrayEquals(conflictOverrides.asStringArray(), args.getAs(argKey, String[].class));
    }

    @Test
    public void testValidate() throws JobConfigValidationException {
        Owner owner = this.createTestOwner("owner_key", "log_level");

        JobConfig config = ImportJob.createJobConfig()
            .setOwner(owner)
            .setStoredFileId("stored_field_id_1")
            .setUploadedFileName("upload_file_name_1");

        config.validate();
    }

    // TODO: Update this test to use the JUnit5 exception handling once this branch catches up with main
    @Test
    public void testValidateNoOwner() {
        JobConfig config = ImportJob.createJobConfig()
            .setStoredFileId("stored_field_id_1")
            .setUploadedFileName("upload_file_name_1");

        try {
            config.validate();
            fail("an expected exception was not thrown");
        }
        catch (JobConfigValidationException e) {
            // Pass!
        }
    }

    @Test
    public void testValidateNoStoredFileId() {
        Owner owner = this.createTestOwner("owner_key_1", "log_level_1");

        JobConfig config = ImportJob.createJobConfig()
            .setOwner(owner)
            .setUploadedFileName("upload_file_name_1");

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    public void testValidateNoUploadedFileName() {
        Owner owner = this.createTestOwner("owner_key_1", "log_level_1");

        JobConfig config = ImportJob.createJobConfig()
            .setOwner(owner)
            .setStoredFileId("stored_field_id_1");

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    public void ensureJobSuccess() throws Exception {
        Owner owner = this.createTestOwner("my-test-owner", "info");
        doReturn(owner).when(ownerCurator).getByKey(eq("my-test-owner"));

        String uploadFileName = "upload_file_name_1";
        String storedFieldId = "stored_field_id_1";
        ConflictOverrides conflictOverrides = new ConflictOverrides(
            Importer.Conflict.SIGNATURE_CONFLICT, Importer.Conflict.MANIFEST_OLD);

        ImportRecord importRecord = new ImportRecord(owner);
        importRecord.setFileName(uploadFileName);

        JobConfig jobConfig = ImportJob.createJobConfig()
            .setOwner(owner)
            .setStoredFileId(storedFieldId)
            .setUploadedFileName(uploadFileName)
            .setConflictOverrides(conflictOverrides);

        ImportJob job = this.buildImportJob();

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        doReturn(jobConfig.getJobArguments()).when(status).getJobArguments();

        when(manifestManager.importStoredManifest(eq(owner), eq(storedFieldId), any(ConflictOverrides.class),
            eq(uploadFileName))).thenReturn(importRecord);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object actualResult = captor.getValue();

        assertEquals(importRecord, actualResult);
    }

    @Test
    public void ensureJobFailure() throws Exception {
        Owner owner = this.createTestOwner("my-test-owner", "info");
        doReturn(owner).when(ownerCurator).getByKey(eq("my-test-owner"));

        String archiveFilePath = "/path/to/some/file.zip";
        ConflictOverrides co = new ConflictOverrides(Importer.Conflict.SIGNATURE_CONFLICT);
        String uploadedFileName = "test.zip";
        String expectedMessage = "Expected Exception Message";

        JobConfig jobConfig = ImportJob.createJobConfig()
            .setOwner(owner)
            .setUploadedFileName(uploadedFileName)
            .setStoredFileId(archiveFilePath)
            .setConflictOverrides(co);

        ImportJob job = this.buildImportJob();

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        doReturn(jobConfig.getJobArguments()).when(status).getJobArguments();

        when(manifestManager.importStoredManifest(eq(owner), eq(archiveFilePath),
            any(ConflictOverrides.class), eq(uploadedFileName)))
            .thenThrow(new ImporterException(expectedMessage));

        Exception e = assertThrows(JobExecutionException.class, () -> job.execute(context));
        assertEquals(expectedMessage, e.getMessage());
        verify(manifestManager).recordImportFailure(eq(owner), eq(e.getCause()), eq(uploadedFileName));
    }


    @Test
    public void ensureJobExceptionThrownIfOwnerNotFound() {
        Owner owner = this.createTestOwner("my-test-owner", "info");
        doReturn(null).when(ownerCurator).getByKey(eq("my-test-owner"));

        String archiveFilePath = "/path/to/some/file.zip";
        ConflictOverrides co = new ConflictOverrides(Importer.Conflict.SIGNATURE_CONFLICT);
        String uploadedFileName = "test.zip";
        String expectedMessage = String.format("Owner %s was not found.", owner.getKey());

        JobConfig jobConfig = ImportJob.createJobConfig()
            .setOwner(owner)
            .setUploadedFileName(uploadedFileName)
            .setStoredFileId(archiveFilePath)
            .setConflictOverrides(co);

        ImportJob job = this.buildImportJob();

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        doReturn(jobConfig.getJobArguments()).when(status).getJobArguments();

        Exception e = assertThrows(JobExecutionException.class, () -> job.execute(context));
        assertEquals(expectedMessage, e.getMessage());
        verify(manifestManager).recordImportFailure(eq(null), eq(e.getCause()), eq(uploadedFileName));
    }
}
