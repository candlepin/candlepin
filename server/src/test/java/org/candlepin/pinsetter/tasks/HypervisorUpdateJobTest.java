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

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.JobCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.core.model.JobStatus.JobState;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.util.GuestMigration;

import com.google.inject.util.Providers;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.ListenerManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.Set;

import javax.inject.Provider;

/**
 * HypervisorUpdateJobTest
 */
public class HypervisorUpdateJobTest extends BaseJobTest{

    private Owner owner;
    private Principal principal;
    private String hypervisorJson;

    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerResource consumerResource;
    private I18n i18n;

    private Provider<GuestMigration> migrationProvider;
    private GuestMigration testMigration;

    @Before
    public void init() {
        super.init();
        i18n = I18nFactory.getI18n(
            getClass(),
            Locale.US,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK
        );
        owner = mock(Owner.class);
        principal = mock(Principal.class);
        ownerCurator = mock(OwnerCurator.class);
        consumerCurator = mock(ConsumerCurator.class);
        consumerResource = mock(ConsumerResource.class);
        when(owner.getKey()).thenReturn("joe");
        when(principal.getUsername()).thenReturn("joe user");

        testMigration = new GuestMigration(consumerCurator, mock(EventFactory.class), mock(EventSink.class));
        migrationProvider = Providers.of(testMigration);

        hypervisorJson =
                "{\"hypervisors\":" +
                "[{" +
                "\"name\" : \"hypervisor_999\"," +
                "\"hypervisorId\" : {\"hypervisorId\":\"uuid_999\"}," +
                "\"guestIds\" : [{\"guestId\" : \"guestId_1_999\"}]" +
                "}]}";
    }

    @Test
    public void createJobDetail() {
        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal, null);
        assertNotNull(detail);
        assertEquals("joe", detail.getJobDataMap().get(JobStatus.TARGET_ID));
        assertEquals("joe user", ((Principal) detail.getJobDataMap().get("principal")).getUsername());
        assertTrue(detail.getJobClass().equals(HypervisorUpdateJob.class));
    }

    @Test
    public void hypervisorUpdateExecCreate() throws JobExecutionException {
        when(ownerCurator.lookupByKey(eq("joe"))).thenReturn(owner);

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal, null);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(consumerCurator.getHostConsumersMap(eq(owner), any(Set.class)))
            .thenReturn(new VirtConsumerMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource,
            i18n, migrationProvider);
        injector.injectMembers(job);
        job.execute(ctx);
        verify(consumerResource).create(any(Consumer.class), eq(principal), anyString(), eq("joe"),
            anyString(), eq(false));
    }

    @Test
    public void reporterIdOnCreateTest() throws JobExecutionException {
        when(ownerCurator.lookupByKey(eq("joe"))).thenReturn(owner);

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal,
            "createReporterId");
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(consumerCurator.getHostConsumersMap(eq(owner), any(Set.class))).thenReturn(
            new VirtConsumerMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource,
            i18n, migrationProvider);
        injector.injectMembers(job);
        job.execute(ctx);
        ArgumentCaptor<Consumer> argument = ArgumentCaptor.forClass(Consumer.class);
        verify(consumerResource).create(argument.capture(), eq(principal), anyString(), eq("joe"),
            anyString(), eq(false));
        assertEquals("createReporterId", argument.getValue().getHypervisorId().getReporterId());
    }

    @Test
    public void hypervisorUpdateExecUpdate() throws JobExecutionException {
        when(ownerCurator.lookupByKey(eq("joe"))).thenReturn(owner);
        Consumer hypervisor = new Consumer();
        String hypervisorId = "uuid_999";
        hypervisor.setHypervisorId(new HypervisorId(hypervisorId));
        VirtConsumerMap vcm = new VirtConsumerMap();
        vcm.add(hypervisorId, hypervisor);
        when(consumerCurator.getHostConsumersMap(eq(owner), any(Set.class))).thenReturn(vcm);

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal, null);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource,
            i18n, migrationProvider);
        injector.injectMembers(job);
        job.execute(ctx);
        verify(consumerResource).performConsumerUpdates(any(Consumer.class), eq(hypervisor),
            any(GuestMigration.class), eq(false));
    }

    @Test
    public void reporterIdOnUpdateTest() throws JobExecutionException {
        when(ownerCurator.lookupByKey(eq("joe"))).thenReturn(owner);
        Consumer hypervisor = new Consumer();
        String hypervisorId = "uuid_999";
        hypervisor.setHypervisorId(new HypervisorId(hypervisorId));
        VirtConsumerMap vcm = new VirtConsumerMap();
        vcm.add(hypervisorId, hypervisor);
        when(consumerCurator.getHostConsumersMap(eq(owner), any(Set.class))).thenReturn(vcm);

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal,
            "updateReporterId");
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource,
            i18n, migrationProvider);
        injector.injectMembers(job);
        job.execute(ctx);
        assertEquals("updateReporterId", hypervisor.getHypervisorId().getReporterId());
    }

    @Test
    public void hypervisorUpdateExecCreateNoHypervisorId() throws JobExecutionException {
        when(ownerCurator.lookupByKey(eq("joe"))).thenReturn(owner);

        hypervisorJson =
                "{\"hypervisors\":" +
                "[{" +
                "\"name\" : \"hypervisor_999\"," +
                "\"guestIds\" : [{\"guestId\" : \"guestId_1_999\"}]" +
                "}]}";

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal, null);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource,
            i18n, migrationProvider);
        injector.injectMembers(job);
        job.execute(ctx);
        verify(consumerResource, never()).create(any(Consumer.class), any(Principal.class),
            anyString(), anyString(), anyString(), eq(false));
    }

    @Test
    public void hypervisorUpdateIgnoresEmptyGuestIds() throws Exception {
        when(ownerCurator.lookupByKey(eq("joe"))).thenReturn(owner);

        hypervisorJson =
                "{\"hypervisors\":" +
                "[{" +
                "\"hypervisorId\" : {\"hypervisorId\" : \"hypervisor_999\"}," +
                "\"name\" : \"hypervisor_999\"," +
                "\"guestIds\" : [{\"guestId\" : \"guestId_1_999\"}, {\"guestId\" : \"\"}]" +
                "}]}";

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal, null);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());

        when(consumerCurator.getHostConsumersMap(eq(owner), any(Set.class)))
            .thenReturn(new VirtConsumerMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource,
            i18n, migrationProvider);
        injector.injectMembers(job);
        job.execute(ctx);
    }

    /*
     * Schedule the job to be executed later even if a similar job exists.
     */
    @Test
    public void dontSkipIfExistsTest() throws JobExecutionException, SchedulerException {

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal, null);
        JobStatus preExistingJobStatus = new JobStatus();
        preExistingJobStatus.setState(JobState.WAITING);
        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource,
            i18n, migrationProvider);
        JobStatus newlyScheduledJobStatus = new JobStatus();

        JobCurator jobCurator = mock(JobCurator.class);
        Scheduler scheduler = mock(Scheduler.class);
        ListenerManager lm = mock(ListenerManager.class);

        when(jobCurator.getByClassAndTarget(anyString(), any(Class.class))).thenReturn(
            preExistingJobStatus);
        when(scheduler.getListenerManager()).thenReturn(lm);
        when(jobCurator.create(any(JobStatus.class))).thenReturn(newlyScheduledJobStatus);

        JobStatus resultStatus = job.scheduleJob(jobCurator, scheduler, detail, null);
        assertEquals(newlyScheduledJobStatus, resultStatus);
    }

    /*
     * Make sure only one test is running at a time.
     */
    @Test
    public void monogamousJobTest() throws JobExecutionException, SchedulerException {
        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal, null);
        JobStatus newJob = new JobStatus(detail);
        JobCurator jobCurator = mock(JobCurator.class);
        when(jobCurator.findNumRunningByClassAndTarget(owner.getKey(), HypervisorUpdateJob.class))
            .thenReturn(1L);

        assertFalse(HypervisorUpdateJob.isSchedulable(jobCurator, newJob));
    }

    @Test
    public void ensureJobFailsWhenAutobindDisabledForTargetOwner() throws Exception {
        // Disabled autobind
        when(owner.isAutobindDisabled()).thenReturn(true);
        when(ownerCurator.lookupByKey(eq("joe"))).thenReturn(owner);

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal, null);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(consumerCurator.getHostConsumersMap(eq(owner), any(Set.class)))
            .thenReturn(new VirtConsumerMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource,
            i18n, migrationProvider);
        injector.injectMembers(job);

        try {
            job.execute(ctx);
            fail("Expected exception due to autobind being disabled.");
        }
        catch (JobExecutionException jee) {
            assertEquals(jee.getCause().getMessage(),
                "Could not update host/guest mapping. Auto-attach is disabled for owner joe.");
        }
    }

}
