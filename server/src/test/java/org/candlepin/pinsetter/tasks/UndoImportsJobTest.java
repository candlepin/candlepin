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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.ImportUpstreamConsumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.RetryJobException;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.sync.ImporterException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.sql.SQLException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;



/**
 * UndoImportsJobTest
 */
@RunWith(MockitoJUnitRunner.class)
public class UndoImportsJobTest extends DatabaseTestFixture {

    @Inject protected I18n i18n;
    @Mock protected CandlepinPoolManager poolManager;
    @Inject protected OwnerCurator ownerCurator;
    @Mock protected SubscriptionServiceAdapter subAdapter;
    @Mock protected Refresher refresher;
    @Mock protected ExporterMetadataCurator exportCurator;
    @Inject protected ImportRecordCurator importRecordCurator;

    @Mock protected JobExecutionContext jobContext;
    protected JobDataMap jobDataMap;

    protected UndoImportsJob undoImportsJob;



    @Before
    public void setUp() {
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);

        // Reset mocks/spys
        reset(this.poolManager);
        reset(this.ownerCurator);
        reset(this.subAdapter);
        reset(this.refresher);
        reset(this.exportCurator);

        reset(this.jobContext);
        this.jobDataMap = new JobDataMap();

        // Setup common behavior
        when(this.jobContext.getMergedJobDataMap()).thenReturn(this.jobDataMap);
        when(this.poolManager.getRefresher(eq(this.subAdapter), anyBoolean())).thenReturn(this.refresher);
        when(this.refresher.setUnitOfWork(any(UnitOfWork.class))).thenReturn(this.refresher);
        when(this.refresher.add(any(Owner.class))).thenReturn(this.refresher);


        this.undoImportsJob = new UndoImportsJob(
            this.i18n, this.ownerCurator, this.poolManager, this.subAdapter,
            this.exportCurator, this.importRecordCurator
        );
    }

    @Test
    public void testImportRecordDeleteWithLogging()
        throws JobExecutionException, IOException, ImporterException, InterruptedException {

        Owner owner = TestUtil.createOwner();
        this.ownerCurator.create(owner);

        Principal principal = new UserPrincipal("JarJarBinks", null, true);
        ExporterMetadata metadata = new ExporterMetadata();

        this.jobDataMap.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        this.jobDataMap.put(JobStatus.TARGET_ID, owner.getKey());
        this.jobDataMap.put(PinsetterJobListener.PRINCIPAL_KEY, principal);

        when(this.ownerCurator.lookupByKey(owner.getKey())).thenReturn(owner);

        when(this.exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner))
            .thenReturn(metadata);

        when(this.poolManager.listPoolsByOwner(owner)).thenReturn(new LinkedList<Pool>());

        this.undoImportsJob.toExecute(this.jobContext);


        List<ImportRecord> records = importRecordCurator.findRecords(owner);
        assertEquals(1, records.size());
        ImportRecord ir = records.get(0);
        assertEquals(ImportRecord.Status.DELETE, ir.getStatus());
    }

    @Test
    public void handleException() throws JobExecutionException {
        // the real thing we want to handle
        doThrow(new NullPointerException()).when(this.ownerCurator).lookupByKey(anyString());

        try {
            this.undoImportsJob.execute(this.jobContext);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertFalse(ex.refireImmediately());
        }
    }

    // If we encounter a runtime job exception, wrapping a SQLException, we should see
    // a refire job exception thrown:
    @Test
    public void refireOnWrappedSQLException() throws JobExecutionException {
        RuntimeException e = new RuntimeException("uh oh", new SQLException("not good"));
        doThrow(e).when(this.ownerCurator).lookupByKey(anyString());

        try {
            this.undoImportsJob.execute(this.jobContext);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertTrue(ex.refireImmediately());
        }
    }

    // If we encounter a runtime job exception, wrapping a SQLException, we should see
    // a refire job exception thrown:
    @Test
    public void refireOnMultiLayerWrappedSQLException() throws JobExecutionException {
        RuntimeException e = new RuntimeException("uh oh", new SQLException("not good"));
        RuntimeException e2 = new RuntimeException("trouble!", e);
        doThrow(e2).when(this.ownerCurator).lookupByKey(anyString());

        try {
            this.undoImportsJob.execute(this.jobContext);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertTrue(ex.refireImmediately());
        }
    }

    @Test
    public void noRefireOnRegularRuntimeException() throws JobExecutionException {
        RuntimeException e = new RuntimeException("uh oh", new NullPointerException());
        doThrow(e).when(this.ownerCurator).lookupByKey(anyString());

        try {
            this.undoImportsJob.execute(this.jobContext);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertFalse(ex.refireImmediately());
        }
    }
}
