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

import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.persistence.LockTimeoutException;
import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;
import javax.persistence.PessimisticLockException;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefreshPoolsJobTest
 */
public class RefreshPoolsJobTest extends BaseJobTest {

    private CandlepinPoolManager pm;
    private OwnerCurator oc;
    private Owner owner;
    private JobExecutionContext ctx;
    private JobDataMap jdm;
    private OwnerServiceAdapter ownerAdapter;
    private SubscriptionServiceAdapter subAdapter;
    private Refresher refresher;

    @Before
    public void setUp() {
        super.init();
        pm = mock(CandlepinPoolManager.class);
        oc = mock(OwnerCurator.class);
        owner = mock(Owner.class);
        ctx = mock(JobExecutionContext.class);
        jdm = mock(JobDataMap.class);
        ownerAdapter = mock(OwnerServiceAdapter.class);
        subAdapter = mock(SubscriptionServiceAdapter.class);
        refresher = mock(Refresher.class);

        when(ctx.getMergedJobDataMap()).thenReturn(jdm);
        when(jdm.getString(eq(JobStatus.TARGET_ID))).thenReturn("someownerkey");
        when(jdm.getBoolean(eq(RefreshPoolsJob.LAZY_REGEN))).thenReturn(true);
        when(oc.getByKey(eq("someownerkey"))).thenReturn(owner);
        when(owner.getDisplayName()).thenReturn("test owner");
        when(pm.getRefresher(eq(subAdapter), eq(ownerAdapter), eq(true))).thenReturn(refresher);
        when(refresher.add(eq(owner))).thenReturn(refresher);
    }

    @Test
    public void execute() throws Exception {
        // test
        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm, subAdapter, ownerAdapter);
        injector.injectMembers(rpj);
        rpj.execute(ctx);

        // verification
        verify(pm).getRefresher(subAdapter, ownerAdapter, true);
        verify(refresher).add(owner);
        verify(refresher).run();
        verify(ctx).setResult(eq("Pools refreshed for owner test owner"));
    }

    @Test
    public void forOwner() {
        Owner owner = mock(Owner.class);
        when(owner.getKey()).thenReturn("owner key");

        JobDetail detail = RefreshPoolsJob.forOwner(owner, true);
        assertNotNull(detail);
        assertNotNull(detail.getJobDataMap());
        assertTrue(detail.requestsRecovery());
        assertEquals("owner key", detail.getJobDataMap().get(JobStatus.TARGET_ID));
    }

    @Test
    public void handleException() throws JobExecutionException {
        // the real thing we want to handle
        doThrow(new NullPointerException()).when(refresher).run();

        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm, subAdapter, ownerAdapter);
        injector.injectMembers(rpj);
        try {
            rpj.execute(ctx);
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
        doThrow(e).when(refresher).run();

        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm, subAdapter, ownerAdapter);
        injector.injectMembers(rpj);
        try {
            rpj.execute(ctx);
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
        doThrow(e2).when(refresher).run();

        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm, subAdapter, ownerAdapter);
        injector.injectMembers(rpj);
        try {
            rpj.execute(ctx);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertTrue(ex.refireImmediately());
        }
    }

    @Test
    public void noRefireOnRegularRuntimeException() throws JobExecutionException {
        RuntimeException e = new RuntimeException("uh oh", new NullPointerException());
        doThrow(e).when(refresher).run();

        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm, subAdapter, ownerAdapter);
        injector.injectMembers(rpj);
        try {
            rpj.execute(ctx);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertFalse(ex.refireImmediately());
        }
    }

    @Test
    public void shouldNotRefireOnGenericPersistenceException() {
        final NullPointerException cause = new NullPointerException();
        final PersistenceException e = new PersistenceException("uh oh", cause);
        doThrow(e).when(refresher).run();

        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm, subAdapter, ownerAdapter);
        injector.injectMembers(rpj);
        try {
            rpj.execute(ctx);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertFalse(ex.refireImmediately());
        }
    }

    @Test
    public void shouldRefireOnLockTimeoutException() {
        final LockTimeoutException e = new LockTimeoutException("uh oh");
        doThrow(e).when(refresher).run();

        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm, subAdapter, ownerAdapter);
        injector.injectMembers(rpj);
        try {
            rpj.execute(ctx);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertTrue(ex.refireImmediately());
        }
    }

    @Test
    public void shouldRefireOnOptimisticLockException() {
        final OptimisticLockException e = new OptimisticLockException("uh oh");
        doThrow(e).when(refresher).run();

        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm, subAdapter, ownerAdapter);
        injector.injectMembers(rpj);
        try {
            rpj.execute(ctx);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertTrue(ex.refireImmediately());
        }
    }

    @Test
    public void shouldRefireOnPessimisticLockException() {
        final PessimisticLockException e = new PessimisticLockException("uh oh");
        doThrow(e).when(refresher).run();

        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm, subAdapter, ownerAdapter);
        injector.injectMembers(rpj);
        try {
            rpj.execute(ctx);
            fail("Expected exception not thrown");
        }
        catch (JobExecutionException ex) {
            assertTrue(ex.refireImmediately());
        }
    }
}
