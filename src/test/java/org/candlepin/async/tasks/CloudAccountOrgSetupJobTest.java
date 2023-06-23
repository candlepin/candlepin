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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.cloudregistration.CloudAccountOrgMismatchException;
import org.candlepin.service.exception.cloudregistration.CouldNotAcquireCloudAccountLockException;
import org.candlepin.service.exception.cloudregistration.CouldNotEntitleOrganizationException;
import org.candlepin.service.model.CloudAccountData;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloudAccountOrgSetupJobTest {

    @Mock
    protected CloudRegistrationAdapter cloudReg;

    @Mock
    protected OwnerCurator ownerCurator;

    @Test
    void testJobConfigSetCloudAccountId() {
        String accountId = TestUtil.randomString();
        JobConfig config = CloudAccountOrgSetupJob.createJobConfig()
            .setCloudAccountId(accountId);

        JobArguments args = config.getJobArguments();

        assertThrows(IllegalArgumentException.class, () -> CloudAccountOrgSetupJob.createJobConfig()
            .setCloudAccountId(""));
        assertThrows(IllegalArgumentException.class, () -> CloudAccountOrgSetupJob.createJobConfig()
            .setCloudAccountId(null));

        assertTrue(args.containsKey(CloudAccountOrgSetupJob.CLOUD_ACCOUNT_ID));
        assertEquals(accountId, args.getAsString(CloudAccountOrgSetupJob.CLOUD_ACCOUNT_ID));
    }

    @Test
    void testJobConfigSetCloudOfferingId() {
        String offeringId = TestUtil.randomString();
        JobConfig config = CloudAccountOrgSetupJob.createJobConfig()
            .setCloudOfferingId(offeringId);

        JobArguments args = config.getJobArguments();

        assertThrows(IllegalArgumentException.class, () -> CloudAccountOrgSetupJob.createJobConfig()
            .setCloudOfferingId(""));
        assertThrows(IllegalArgumentException.class, () -> CloudAccountOrgSetupJob.createJobConfig()
            .setCloudOfferingId(null));

        assertTrue(args.containsKey(CloudAccountOrgSetupJob.OFFERING_ID));
        assertEquals(offeringId, args.getAsString(CloudAccountOrgSetupJob.OFFERING_ID));
    }

    @Test
    void testJobConfigSetCloudProvider() {
        String expectedCloudProvider = "azure";
        JobConfig config = CloudAccountOrgSetupJob.createJobConfig()
            .setCloudProvider(expectedCloudProvider);

        JobArguments args = config.getJobArguments();

        assertThrows(IllegalArgumentException.class, () -> CloudAccountOrgSetupJob.createJobConfig()
            .setCloudProvider(null));

        assertTrue(args.containsKey(CloudAccountOrgSetupJob.CLOUD_PROVIDER));
        assertEquals(expectedCloudProvider, args.getAsString(CloudAccountOrgSetupJob.CLOUD_PROVIDER));
    }

    @Test
    void testJobConfigSetOwnerKey() {
        String ownerKey = TestUtil.randomString();
        JobConfig config = CloudAccountOrgSetupJob.createJobConfig()
            .setOwnerKey(ownerKey);

        JobArguments args = config.getJobArguments();

        assertThrows(IllegalArgumentException.class, () -> CloudAccountOrgSetupJob.createJobConfig()
            .setOwnerKey(""));

        assertTrue(args.containsKey(CloudAccountOrgSetupJob.OWNER_KEY));
        assertEquals(ownerKey, args.getAsString(CloudAccountOrgSetupJob.OWNER_KEY));
    }

    @Test
    void ensureJobSuccessWithNewAnonymousOrganization()
        throws JobExecutionException, CouldNotAcquireCloudAccountLockException {
        when(cloudReg.setupCloudAccountOrg(anyString(), anyString(), any(), anyString()))
            .thenReturn(new CloudAccountData("owner_key", true));

        CloudAccountOrgSetupJob regJob = new CloudAccountOrgSetupJob(cloudReg, ownerCurator);

        String offering = TestUtil.randomString("offering");
        CloudAccountOrgSetupJob.CloudAccountOrgSetupJobConfig jobConfig =
            CloudAccountOrgSetupJob.createJobConfig()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudOfferingId(offering)
            .setCloudProvider(TestUtil.randomString())
            .setOwnerKey(TestUtil.randomString());

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        when(status.getJobArguments()).thenReturn(jobConfig.getJobArguments());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        regJob.execute(context);

        verify(context).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertEquals(String.format("Entitled offering %s to owner owner_key (anonymous: true).", offering),
            result);
    }

    @Test
    void ensureJobSuccessWithNewNonAnonymousOrganization()
        throws JobExecutionException, CouldNotAcquireCloudAccountLockException {
        when(cloudReg.setupCloudAccountOrg(anyString(), anyString(), any(), anyString()))
            .thenReturn(new CloudAccountData("owner_key", false));

        CloudAccountOrgSetupJob regJob = new CloudAccountOrgSetupJob(cloudReg, ownerCurator);

        String offering = TestUtil.randomString("offering");
        CloudAccountOrgSetupJob.CloudAccountOrgSetupJobConfig jobConfig =
            CloudAccountOrgSetupJob.createJobConfig()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudOfferingId(offering)
            .setCloudProvider(TestUtil.randomString())
            .setOwnerKey(TestUtil.randomString());

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        when(status.getJobArguments()).thenReturn(jobConfig.getJobArguments());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        regJob.execute(context);

        verify(context).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertEquals(String.format("Entitled offering %s to owner owner_key (anonymous: false).", offering),
            result);
    }

    @Test
    void ensureJobExceptionThrownIfLockIsNotAcquired() throws CouldNotAcquireCloudAccountLockException {
        when(cloudReg.setupCloudAccountOrg(anyString(), anyString(), any(), anyString())).thenThrow(
            CouldNotAcquireCloudAccountLockException.class);

        CloudAccountOrgSetupJob regJob = new CloudAccountOrgSetupJob(cloudReg, ownerCurator);

        CloudAccountOrgSetupJob.CloudAccountOrgSetupJobConfig jobConfig =
            CloudAccountOrgSetupJob.createJobConfig()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setCloudProvider(TestUtil.randomString())
            .setOwnerKey(TestUtil.randomString());

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        when(status.getJobArguments()).thenReturn(jobConfig.getJobArguments());

        assertThrows(JobExecutionException.class, () -> regJob.execute(context));
    }

    @Test
    void ensureJobExceptionThrownIfCouldNotEntitleOrganization()
        throws CouldNotAcquireCloudAccountLockException {
        when(cloudReg.setupCloudAccountOrg(anyString(), anyString(), any(), anyString())).thenThrow(
            CouldNotEntitleOrganizationException.class);

        CloudAccountOrgSetupJob regJob = new CloudAccountOrgSetupJob(cloudReg, ownerCurator);

        CloudAccountOrgSetupJob.CloudAccountOrgSetupJobConfig jobConfig =
            CloudAccountOrgSetupJob.createJobConfig()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setCloudProvider(TestUtil.randomString())
            .setOwnerKey(TestUtil.randomString());


        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        when(status.getJobArguments()).thenReturn(jobConfig.getJobArguments());

        assertThrows(JobExecutionException.class, () -> regJob.execute(context));
    }

    @Test
    void shouldThrowJobExceptionWhenCloudAccountOrgMismatchHappens()
        throws CouldNotAcquireCloudAccountLockException {
        when(cloudReg.setupCloudAccountOrg(anyString(), anyString(), any(), anyString())).thenThrow(
            CloudAccountOrgMismatchException.class);

        CloudAccountOrgSetupJob regJob = new CloudAccountOrgSetupJob(cloudReg, ownerCurator);

        CloudAccountOrgSetupJob.CloudAccountOrgSetupJobConfig jobConfig =
            CloudAccountOrgSetupJob.createJobConfig()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setCloudProvider(TestUtil.randomString())
            .setOwnerKey(TestUtil.randomString());

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        when(status.getJobArguments()).thenReturn(jobConfig.getJobArguments());

        assertThrows(JobExecutionException.class, () -> regJob.execute(context));
    }

    @Test
    void ensureJobExceptionThrownIllegalState() throws CouldNotAcquireCloudAccountLockException {
        when(cloudReg.setupCloudAccountOrg(anyString(), anyString(), any(), anyString()))
            .thenReturn(new CloudAccountData("owner_key", null));

        CloudAccountOrgSetupJob regJob = new CloudAccountOrgSetupJob(cloudReg, ownerCurator);

        CloudAccountOrgSetupJob.CloudAccountOrgSetupJobConfig jobConfig =
            CloudAccountOrgSetupJob.createJobConfig()
                .setCloudAccountId(TestUtil.randomString())
                .setCloudOfferingId(TestUtil.randomString())
                .setCloudProvider(TestUtil.randomString())
                .setOwnerKey(TestUtil.randomString());


        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        when(status.getJobArguments()).thenReturn(jobConfig.getJobArguments());

        assertThrows(IllegalStateException.class, () -> regJob.execute(context));
    }

    @Test
    void testJobConfigValidation() throws JobConfigValidationException {
        CloudAccountOrgSetupJob.CloudAccountOrgSetupJobConfig jobConfig = CloudAccountOrgSetupJob
            .createJobConfig()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setCloudProvider(TestUtil.randomString())
            .setOwnerKey(TestUtil.randomString());
        jobConfig.validate();
    }

    @Test
    void testAnonymousOwnerRecord() {
        new CloudAccountData("owner_key", true);
        assertThrows(IllegalArgumentException.class, () -> new CloudAccountData(null, true));
        assertThrows(IllegalArgumentException.class, () -> new CloudAccountData("", true));
    }

}
