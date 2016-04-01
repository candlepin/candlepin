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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.candlepin.controller.ManifestManager;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.Importer.Conflict;
import org.candlepin.sync.ImporterException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@RunWith(MockitoJUnitRunner.class)
public class ImportJobTest {

    @Mock private OwnerCurator ownerCurator;
    @Mock private JobExecutionContext ctx;
    @Mock private ManifestManager manifestManager;

    private ImportJob job;
    private Owner owner;

    @Before
    public void setup() {
        owner = new Owner("my-test-owner");
        job = new ImportJob(ownerCurator, manifestManager);
    }

    @Test
    public void checkJobDetail() throws IOException {
        String archiveFilePath = "/path/to/some/file.zip";
        String expectedOriginalUploadFileName = "test.zip";

        ConflictOverrides expectedConflictOverrides = new ConflictOverrides(Conflict.values());

        JobDetail detail = job.scheduleImport(owner, archiveFilePath, expectedOriginalUploadFileName,
            expectedConflictOverrides);
        JobDataMap dataMap = detail.getJobDataMap();

        assertEquals(dataMap.get(JobStatus.OWNER_ID), owner.getKey());
        assertEquals(dataMap.get(JobStatus.TARGET_ID), owner.getKey());
        assertEquals(dataMap.get(JobStatus.TARGET_TYPE), JobStatus.TargetType.OWNER);
        assertEquals(dataMap.get(ImportJob.STORED_FILE_ID), archiveFilePath);
        assertEquals(dataMap.get(ImportJob.UPLOADED_FILE_NAME), expectedOriginalUploadFileName);

        String[] overrides = (String[]) dataMap.get(ImportJob.CONFLICT_OVERRIDES);
        assertArrayEquals(expectedConflictOverrides.asStringArray(), overrides);
    }

    @Test
    public void enusureJobSuccess() throws JobExecutionException, ImporterException, IOException {
        String archiveFilePath = "/path/to/some/file.zip";
        ConflictOverrides co = new ConflictOverrides();
        ImportRecord record = new ImportRecord(owner);
        String uploadedFileName = "test.zip";

        JobDetail detail = job.scheduleImport(owner, archiveFilePath, uploadedFileName, co);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(ownerCurator.lookupByKey(eq(owner.getKey()))).thenReturn(owner);
        when(manifestManager.importStoredManifest(eq(owner), any(String.class),
            any(ConflictOverrides.class), eq(uploadedFileName))).thenReturn(record);
        job.execute(ctx);

        verify(ctx).setResult(eq(record));
    }

    @Test
    public void ensureJobFailure() throws Exception {
        String archiveFilePath = "/path/to/some/file.zip";
        ConflictOverrides co = new ConflictOverrides();
        String uploadedFileName = "test.zip";
        String expectedMessage = "Expected Exception Message";

        JobDetail detail = job.scheduleImport(owner, archiveFilePath, uploadedFileName, co);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(ownerCurator.lookupByKey(eq(owner.getKey()))).thenReturn(owner);
        when(manifestManager.importStoredManifest(eq(owner), any(String.class), any(ConflictOverrides.class),
            eq(uploadedFileName))).thenThrow(new ImporterException(expectedMessage));

        try {
            job.execute(ctx);
            fail("Expected exception to be thrown");
        }
        catch (JobExecutionException e) {
            // Expected
        }
        verify(ctx).setResult(eq(expectedMessage));
    }

    @Test
    public void ensureNoOpIfOwnerDoesNotExist() throws Exception {
        String archiveFilePath = "/path/to/some/file.zip";
        ConflictOverrides co = new ConflictOverrides();
        String uploadedFileName = "test.zip";
        String expectedMessage = "Nothing to do. Owner no longer exists.";

        JobDetail detail = job.scheduleImport(owner, archiveFilePath, uploadedFileName, co);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(ownerCurator.lookupByKey(eq(owner.getKey()))).thenReturn(null);

        job.execute(ctx);
        verify(ctx).setResult(eq(expectedMessage));
    }

}
