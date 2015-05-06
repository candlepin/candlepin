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
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.sync.ImporterException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;

import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.sql.SQLException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;



/**
 * UndoImportsJobTest
 */
public class UndoImportsJobTest extends DatabaseTestFixture {

    @Inject protected I18n i18n;

    @Inject protected CandlepinPoolManager poolManagerBase;
    @Inject protected ImportRecordCurator importRecordCurator;
    @Inject protected ExporterMetadataCurator exportCuratorBase;
    @Inject protected ConsumerTypeCurator consumerTypeCurator;

    protected CandlepinPoolManager poolManager;
    protected OwnerCurator ownerCurator;
    protected SubscriptionServiceAdapter subAdapter;
    protected Refresher refresher;
    protected ExporterMetadataCurator exportCurator;

    protected JobExecutionContext jobContext;
    protected JobDataMap jobDataMap;

    protected UndoImportsJob undoImportsJob;



    @Before
    public void setUp() {
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);

        // Reset mocks/spys/objects
        this.poolManager = mock(CandlepinPoolManager.class);
        this.ownerCurator = mock(OwnerCurator.class);
        this.subAdapter = mock(SubscriptionServiceAdapter.class);
        this.refresher = mock(Refresher.class);
        this.exportCurator = mock(ExporterMetadataCurator.class);

        this.jobContext = mock(JobExecutionContext.class);
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
    public void testUndoImport() throws JobExecutionException, IOException, ImporterException {
        // We need proper curators for this test
        this.poolManager = this.poolManagerBase;
        this.ownerCurator = super.ownerCurator;
        this.exportCurator = this.exportCuratorBase;

        this.undoImportsJob = new UndoImportsJob(
            this.i18n, this.ownerCurator, this.poolManager, this.subAdapter,
            this.exportCurator, this.importRecordCurator
        );

        // Create owner w/upstream consumer
        Owner owner1 = TestUtil.createOwner();
        Owner owner2 = TestUtil.createOwner();
        ConsumerType type = new ConsumerType("system");
        UpstreamConsumer uc1 = new UpstreamConsumer("uc1", null, type, "uc1");
        UpstreamConsumer uc2 = new UpstreamConsumer("uc2", null, type, "uc2");
        this.consumerTypeCurator.create(type);
        this.ownerCurator.create(owner1);
        this.ownerCurator.create(owner2);
        owner1.setUpstreamConsumer(uc1);
        owner1.setUpstreamConsumer(uc2);
        this.ownerCurator.merge(owner1);
        this.ownerCurator.merge(owner2);

        // Create metadata
        ExporterMetadata metadata1 = new ExporterMetadata(ExporterMetadata.TYPE_PER_USER, new Date(), owner1);
        ExporterMetadata metadata2 = new ExporterMetadata(ExporterMetadata.TYPE_PER_USER, new Date(), owner2);
        this.exportCurator.create(metadata1);
        this.exportCurator.create(metadata2);

        // Create pools w/upstream pool IDs
        Product prod1 = TestUtil.createProduct("prod1", "prod1", owner1);
        Product prod2 = TestUtil.createProduct("prod2", "prod2", owner1);
        Product prod3 = TestUtil.createProduct("prod3", "prod3", owner2);
        this.productCurator.create(prod1);
        this.productCurator.create(prod2);
        this.productCurator.create(prod3);

        Pool pool1 = TestUtil.createPool(owner1, prod1);
        Pool pool2 = TestUtil.createPool(owner1, prod2);
        Pool pool3 = TestUtil.createPool(owner2, prod3);
        this.poolManager.createPool(pool1);
        this.poolManager.createPool(pool2);
        this.poolManager.createPool(pool3);

        // Verify initial state
        assertEquals(2, this.poolManager.listPoolsByOwner(owner1).size());
        assertEquals(1, this.poolManager.listPoolsByOwner(owner2).size());
        assertEquals(metadata1, exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner1));
        assertEquals(metadata2, exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner2));
        assertEquals(0, this.importRecordCurator.findRecords(owner1).size());
        assertEquals(0, this.importRecordCurator.findRecords(owner2).size());

        // Execute job
        Principal principal = new UserPrincipal("JarJarBinks", null, true);

        this.jobDataMap.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        this.jobDataMap.put(JobStatus.TARGET_ID, owner1.getKey());
        this.jobDataMap.put(PinsetterJobListener.PRINCIPAL_KEY, principal);

        this.undoImportsJob.toExecute(this.jobContext);

        // Verify deletions
        assertEquals(1, this.poolManager.listPoolsByOwner(owner1).size());
        assertEquals(pool2, this.poolManager.listPoolsByOwner(owner1).get(0));
        assertEquals(1, this.poolManager.listPoolsByOwner(owner2).size());
        assertEquals(pool3, this.poolManager.listPoolsByOwner(owner2).get(0));
        assertNull(exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner1));
        assertEquals(metadata2, exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner2));
        assertNull(owner1.getUpstreamConsumer());

        List<ImportRecord> records = this.importRecordCurator.findRecords(owner1);
        assertEquals(1, records.size());
        assertEquals(ImportRecord.Status.DELETE, records.get(0).getStatus());

        assertEquals(0, this.importRecordCurator.findRecords(owner2).size());
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
