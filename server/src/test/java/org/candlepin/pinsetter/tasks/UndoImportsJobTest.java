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
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.Product;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.service.OwnerServiceAdapter;
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
import java.util.Arrays;
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
    @Inject protected UeberCertificateGenerator ueberCertGenerator;

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
        when(this.poolManager.getRefresher(eq(this.subAdapter), any(OwnerServiceAdapter.class), anyBoolean()))
            .thenReturn(this.refresher);
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
        ConsumerType type = TestUtil.createConsumerType();
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
        Pool pool1 = this.createPool("pool1", owner1, true, PoolType.NORMAL);
        Pool pool2 = this.createPool("pool2", owner1, true, PoolType.BONUS);
        Pool pool3 = this.createPool("pool3", owner1, false, PoolType.NORMAL);
        Pool pool4 = this.createPool("pool4", owner1, false, PoolType.BONUS);
        Pool pool5 = this.createPool("pool5", owner1, true, PoolType.ENTITLEMENT_DERIVED);
        Pool pool6 = this.createPool("pool6", owner1, false, PoolType.ENTITLEMENT_DERIVED);
        Pool pool7 = this.createPool("pool7", owner2, true, PoolType.NORMAL);
        Pool pool8 = this.createPool("pool8", owner2, true, PoolType.BONUS);
        Pool pool9 = this.createPool("pool9", owner2, true, PoolType.ENTITLEMENT_DERIVED);

        // Create an ueber certificate for the owner.
        UeberCertificate uebercert = ueberCertGenerator.generate(owner1.getKey(),
            this.setupAdminPrincipal("test_admin"));
        assertNotNull(uebercert);

        // Verify initial state
        assertEquals(
            Arrays.asList(pool1, pool2, pool3, pool4, pool5, pool6),
            this.poolManager.listPoolsByOwner(owner1).list()
        );
        assertEquals(Arrays.asList(pool7, pool8, pool9), this.poolManager.listPoolsByOwner(owner2).list());
        assertEquals(metadata1, exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner1));
        assertEquals(metadata2, exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner2));
        assertEquals(0, this.importRecordCurator.findRecords(owner1).list().size());
        assertEquals(0, this.importRecordCurator.findRecords(owner2).list().size());

        // Execute job
        Principal principal = new UserPrincipal("JarJarBinks", null, true);

        this.jobDataMap.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        this.jobDataMap.put(JobStatus.TARGET_ID, owner1.getId());
        this.jobDataMap.put(UndoImportsJob.OWNER_KEY, owner1.getKey());
        this.jobDataMap.put(PinsetterJobListener.PRINCIPAL_KEY, principal);

        beginTransaction(); //since we locking owner we need start transaction
        this.undoImportsJob.toExecute(this.jobContext);
        commitTransaction();

        // Verify deletions -- Ueber pools should not get deleted.
        assertEquals(Arrays.asList(pool3, pool4, pool5, pool6),
            this.poolManager.listPoolsByOwner(owner1).list());

        assertEquals(Arrays.asList(pool7, pool8, pool9), this.poolManager.listPoolsByOwner(owner2).list());
        assertNull(exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner1));
        assertEquals(metadata2, exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner2));
        assertNull(owner1.getUpstreamConsumer());

        List<ImportRecord> records = this.importRecordCurator.findRecords(owner1).list();
        assertEquals(1, records.size());
        assertEquals(ImportRecord.Status.DELETE, records.get(0).getStatus());

        assertEquals(0, this.importRecordCurator.findRecords(owner2).list().size());
    }

    protected Pool createPool(String name, Owner owner, boolean keepSourceSub, PoolType type) {
        Product product = this.createProduct(name, name, owner);

        Pool pool = TestUtil.createPool(owner, product);
        if (!keepSourceSub) {
            pool.setSourceSubscription(null);
        }

        this.poolCurator.create(pool);

        Consumer consumer;
        Entitlement entitlement;

        switch (type) {
            // TODO: Others as necessary

            case ENTITLEMENT_DERIVED:
                pool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
                consumer = TestUtil.createConsumer(owner);
                entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);

                this.consumerTypeCurator.create(consumer.getType());
                this.consumerCurator.create(consumer);
                this.entitlementCurator.create(entitlement);

                pool.setSourceEntitlement(entitlement);
                break;

            case BONUS:
                pool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
                break;

            default:
                // Required by checkstyle.
        }

        this.poolCurator.merge(pool);

        return pool;
    }

    @Test
    public void handleException() throws JobExecutionException {
        // the real thing we want to handle
        doThrow(new NullPointerException()).when(this.ownerCurator).lockAndLoadById(anyString());

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
        doThrow(e).when(this.ownerCurator).lockAndLoadById(anyString());

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
        doThrow(e2).when(this.ownerCurator).lockAndLoadById(anyString());

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
        doThrow(e).when(this.ownerCurator).lockAndLoadById(anyString());

        try {
            this.undoImportsJob.execute(this.jobContext);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertFalse(ex.refireImmediately());
        }
    }
}
