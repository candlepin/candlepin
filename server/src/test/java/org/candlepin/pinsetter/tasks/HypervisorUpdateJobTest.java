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
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.resource.ConsumerResource;

import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * HypervisorUpdateJobTest
 */
public class HypervisorUpdateJobTest {

    private Owner owner;
    private Principal principal;
    private String hypervisorJson;

    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerResource consumerResource;


    @Before
    public void init() {
        owner = mock(Owner.class);
        principal = mock(Principal.class);
        ownerCurator = mock(OwnerCurator.class);
        consumerCurator = mock(ConsumerCurator.class);
        consumerResource = mock(ConsumerResource.class);
        when(owner.getKey()).thenReturn("joe");
        when(principal.getUsername()).thenReturn("joe user");

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
        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal);
        assertNotNull(detail);
        assertEquals("joe", detail.getJobDataMap().get(JobStatus.TARGET_ID));
        assertEquals("joe user", ((Principal) detail.getJobDataMap().get("principal")).getUsername());
        assertTrue(detail.getJobClass().equals(HypervisorUpdateJob.class));
    }

    @Test
    public void hypervisorUpdateExecCreate() throws JobExecutionException {
        when(ownerCurator.lookupByKey(eq("joe"))).thenReturn(owner);

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource);
        job.execute(ctx);
        verify(consumerResource).create(any(Consumer.class),
                                        eq(principal),
                                        anyString(),
                                        eq("joe"),
                                        anyString(),
                                        eq(false));
    }

    @Test
    public void hypervisorUpdateExecUpdate() throws JobExecutionException {
        when(ownerCurator.lookupByKey(eq("joe"))).thenReturn(owner);
        Consumer hypervisor = new Consumer();
        String hypervisorId = "uuid_999";
        hypervisor.setHypervisorId(new HypervisorId(hypervisorId));
        when(consumerCurator.getHypervisor(eq(hypervisorId), eq(owner))).thenReturn(hypervisor);

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource);
        job.execute(ctx);
        verify(consumerResource).updateConsumer(eq(hypervisor.getUuid()), any(Consumer.class));

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

        JobDetail detail = HypervisorUpdateJob.forOwner(owner, hypervisorJson, true, principal);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());

        HypervisorUpdateJob job = new HypervisorUpdateJob(ownerCurator, consumerCurator, consumerResource);
        job.execute(ctx);
        verify(consumerResource, never()).create(any(Consumer.class),
                                        any(Principal.class),
                                        anyString(),
                                        anyString(),
                                        anyString(),
                                        eq(false));
    }
}
