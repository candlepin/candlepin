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
package org.candlepin.spec.jobs;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.resource.client.v1.ConsumerApi;
import org.candlepin.resource.client.v1.JobsApi;
import org.candlepin.resource.client.v1.OwnerApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Test the /status resource
 */
@SpecTest
class JobStatusSpecTest {

    private static ApiClient client;
    private static OwnerApi ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static ConsumerApi consumerApi;
    private static JobsClient jobsClient;
    private ApiClient userClient;
    private OwnerDTO owner;
    private UserDTO user;
    private ProductDTO product;
    private PoolDTO pool;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        jobsClient = client.jobs();
        consumerApi = client.consumers();
    }

    @BeforeEach
    void beforeEach() {
        owner = ownerApi.createOwner(Owners.random());
        user = UserUtil.createUser(client, owner);

        userClient = ApiClients.basic(user.getUsername(), user.getPassword());

        product = Products.random();
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);

        pool = Pools.random().quantity(4L).productId(product.getId());
        pool = ownerApi.createPool(owner.getKey(), pool);
    }

    @Test
    public void shouldFindEmptyListOwnerNotExist() {
        List<AsyncJobStatusDTO> jobs = jobsClient.listMatchingJobStatusForOrg("totally-made-up",
            null, null);
        assertThat(jobs).isEmpty();
    }

    @Test
    public void shouldCancelAJob() throws Exception {
        jobsClient.setSchedulerStatus(false);
        try {
            AsyncJobStatusDTO jobStatus = ownerApi.healEntire(owner.getKey());
            // make sure we see a job waiting to go
            List<AsyncJobStatusDTO> statuses = jobsClient.listMatchingJobStatusForOrg(owner.getKey(),
                Set.of(jobStatus.getId()), "CREATED");
            assertThat(statuses).hasSize(1);

            jobsClient.cancelJob(jobStatus.getId());
            // make sure we see a job canceled
            statuses = jobsClient.listMatchingJobStatusForOrg(owner.getKey(),
                Set.of(jobStatus.getId()), "CANCELED");
            assertThat(statuses).hasSize(1);

            jobsClient.setSchedulerStatus(true);
            sleep(1); // let the job queue drain..
            // make sure job didn't flip to FINISHED
            statuses = jobsClient.listMatchingJobStatusForOrg(owner.getKey(),
                Set.of(jobStatus.getId()), "CANCELED");
            assertThat(statuses).hasSize(1);
        }
        finally {
            jobsClient.setSchedulerStatus(true);
        }
    }

    @Test
    public void shouldContainSystemIdForAsync() throws Exception {
        ownerApi.healEntire(owner.getKey());
        ConsumerDTO consumer = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerApi = consumerClient.consumers();
        AsyncJobStatusDTO bindStatus = AsyncJobStatusDTO.fromJson(consumerApi.bind(consumer.getUuid(),
            null, List.of(product.getId()), null, null, null, true, null, null));
        assertEquals(consumer.getUuid(), bindStatus.getPrincipal());

        bindStatus = jobsClient.waitForJob(bindStatus.getId());
        assertEquals("FINISHED", bindStatus.getState());
    }

    @Test
    public void shouldAllowAdminToViewJobStatus() {
        AsyncJobStatusDTO jobStatus = ownerApi.healEntire(owner.getKey());
        AsyncJobStatusDTO checkStatus = jobsClient.waitForJob(jobStatus.getId());
        assertThatJob(checkStatus).isFinished();

        AsyncJobStatusDTO newStatus = jobsClient.getJobStatus(jobStatus.getId());
        assertEquals(jobStatus.getId(), newStatus.getId());
    }

    @Test
    public void shouldRunJobsConcurrently() throws InterruptedException {
        int totalThreads = 6;
        List<Thread> threads = new ArrayList<>();
        List<AsyncJobStatusDTO> jobs = new ArrayList<>();

        // First create as many owners as the total threads we have
        List<OwnerDTO> owners = new ArrayList<>();
        for (int i = 0; i < totalThreads; i++) {
            owners.add(ownerApi.createOwner(Owners.random()));
        }

        // For each owner create a Thread which refreshes that owner, and saves the job status
        for (OwnerDTO owner : owners) {
            Thread t = new Thread(() -> jobs.add(ownerApi.healEntire(owner.getKey())));
            threads.add(t);
        }
        assertEquals(totalThreads, threads.size());

        // Run all the threads, then wait for them to complete
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }

        // Check that all jobs finished successfully
        for (AsyncJobStatusDTO job : jobs) {
            AsyncJobStatusDTO jobStatus = jobsClient.waitForJob(job);
            assertEquals("FINISHED", jobStatus.getState());
        }
    }

    @Test
    public void shouldAllUserToViewStatusOfOwnJob() {
        JobsApi jobsApi = userClient.jobs();
        OwnerApi ownerApi1 = userClient.owners();

        AsyncJobStatusDTO jobStatus = ownerApi1.healEntire(owner.getKey());
        AsyncJobStatusDTO checkStatus = jobsClient.waitForJob(jobStatus);
        assertThatJob(checkStatus).isFinished();

        AsyncJobStatusDTO newStatus = jobsApi.getJobStatus(jobStatus.getId());
        assertEquals(jobStatus.getId(), newStatus.getId());
        assertEquals(user.getUsername(), newStatus.getPrincipal());
    }

    @Test
    public void shouldAllowPagingOfJobs() {
        AsyncJobStatusDTO job1 = ownerApi.healEntire(owner.getKey());
        AsyncJobStatusDTO job2 = ownerApi.healEntire(owner.getKey());
        AsyncJobStatusDTO job3 = ownerApi.healEntire(owner.getKey());
        List<AsyncJobStatusDTO> jobs = jobsClient.listJobStatuses(
            Set.of(job1.getId(), job2.getId(), job3.getId()), null, null, Set.of(owner.getKey()), null,
            null, null, null, null, 2, 1, null, null);
        // Since we're not setting the order, we can't guarantee which job we'll get, just that we'll
        // get exactly one of them.
        assertEquals(1, jobs.size());
        List<String> ids = List.of(job1.getId(), job2.getId(), job3.getId());
        List<AsyncJobStatusDTO> result = jobs.stream()
            .filter(x -> ids.contains(x.getId()))
            .collect(Collectors.toList());
        assertEquals(1, result.size());
    }

    @Test
    public void shouldAllowSortingOfJobs() {
        AsyncJobStatusDTO job1 = ownerApi.healEntire(owner.getKey());
        AsyncJobStatusDTO job2 = ownerApi.healEntire(owner.getKey());
        AsyncJobStatusDTO job3 = ownerApi.healEntire(owner.getKey());
        List<AsyncJobStatusDTO> jobs = jobsClient.listJobStatuses(
            Set.of(job1.getId(), job2.getId(), job3.getId()), null, null, Set.of(owner.getKey()), null,
            null, null, null, null, null, null, "asc", "id");
        assertEquals(3, jobs.size());
        // Verify that the IDs are all in ascending order
        String lastId = null;
        for (AsyncJobStatusDTO job : jobs) {
            if (lastId != null) {
                assertThat(lastId.compareTo(job.getId())).isLessThan(0);
            }
            lastId = job.getId();
        }
    }

    @Test
    public void shouldAllowPagingAndSortingOfJobs() {
        AsyncJobStatusDTO job1 = ownerApi.healEntire(owner.getKey());
        AsyncJobStatusDTO job2 = ownerApi.healEntire(owner.getKey());
        AsyncJobStatusDTO job3 = ownerApi.healEntire(owner.getKey());
        List<AsyncJobStatusDTO> jobs = jobsClient.listJobStatuses(
            Set.of(job1.getId(), job2.getId(), job3.getId()), null, null, Set.of(owner.getKey()), null,
            null, null, null, null, 3, 1, "desc", "id");
        assertEquals(1, jobs.size());
        assertEquals(job1.getId(), jobs.get(0).getId());
    }

    @Test
    public void shouldAllowUserToViewConsumerJobStatus() throws Exception {
        ConsumerDTO consumer = Consumers.random(owner);
        consumer = consumerApi.createConsumer(consumer, user.getUsername(), owner.getKey(), null, true);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerApi = consumerClient.consumers();
        AsyncJobStatusDTO bindStatus = AsyncJobStatusDTO.fromJson(consumerApi.bind(consumer.getUuid(),
            null, List.of(product.getId()), null, null, null, true, null, null));
        assertEquals(consumer.getUuid(), bindStatus.getPrincipal());
        JobsApi jobsApi = userClient.jobs();
        AsyncJobStatusDTO userStatus = jobsApi.getJobStatus(bindStatus.getId());
        assertEquals(bindStatus.getId(), userStatus.getId());

        // wait for job to complete, or test clean up will conflict with the asynchronous job.
        jobsClient.waitForJob(bindStatus.getId());
    }

    @Test
    public void shouldNotAllowUserToCancelJobFromAnotherUser() {
        jobsClient.setSchedulerStatus(false);
        String jobId = null;

        try {
            AsyncJobStatusDTO job = userClient.owners().healEntire(owner.getKey());
            UserDTO otherUser = UserUtil.createUser(client, owner);
            ApiClient otherUserClient = ApiClients.basic(otherUser.getUsername(), otherUser.getPassword());

            assertForbidden(() -> otherUserClient.jobs().cancelJob(job.getId()));
            jobId = job.getId();
        }
        finally {
            jobsClient.setSchedulerStatus(true);
            AsyncJobStatusDTO statusDTO = jobsClient.waitForJob(jobId);
            assertThatJob(statusDTO).isFinished();
        }
    }

    @Test
    public void shouldAllowUserToCancelJobItInitiated() throws Exception {
        jobsClient.setSchedulerStatus(false);
        try {
            AsyncJobStatusDTO jobStatus = userClient.owners().healEntire(owner.getKey());
            // make sure we see a job waiting to go
            List<AsyncJobStatusDTO> statuses = jobsClient.listMatchingJobStatusForOrg(owner.getKey(),
                Set.of(jobStatus.getId()), "CREATED");
            assertThat(statuses).hasSize(1);

            AsyncJobStatusDTO job = userClient.jobs().cancelJob(jobStatus.getId());
            AsyncJobStatusDTO checkStatus = jobsClient.waitForJob(job);
            assertThatJob(checkStatus).isCanceled();
            // make sure we see a job canceled
            statuses = jobsClient.listMatchingJobStatusForOrg(owner.getKey(),
                Set.of(jobStatus.getId()), "CANCELED");
            assertThat(statuses).hasSize(1);

            jobsClient.setSchedulerStatus(true);
            sleep(1); // let the job queue drain..
            // make sure job didn't flip to FINISHED
            statuses = jobsClient.listMatchingJobStatusForOrg(owner.getKey(),
                Set.of(jobStatus.getId()), "CANCELED");
            assertThat(statuses).hasSize(1);
        }
        finally {
            jobsClient.setSchedulerStatus(true);
        }
    }

    @Test
    public void shouldNotAllowUserToCancelJobItDidNotInitiate() throws Exception {
        jobsClient.setSchedulerStatus(false);
        try {
            ConsumerDTO consumer = client.consumers().createConsumer(Consumers.random(owner));
            ApiClient consumerClient = ApiClients.ssl(consumer);
            consumerApi = consumerClient.consumers();
            AsyncJobStatusDTO bindStatus = AsyncJobStatusDTO.fromJson(consumerApi.bind(consumer.getUuid(),
                null, List.of(product.getId()), null, null, null, true, null, null));

            assertForbidden(() -> userClient.jobs().cancelJob(bindStatus.getId()));
        }
        finally {
            jobsClient.setSchedulerStatus(true);
        }
    }

    @Test
    public void shouldNotAllowUserToViewJobStatusOutsideManagedOrg() throws Exception {
        OwnerDTO otherOwner = ownerApi.createOwner(Owners.random());
        UserDTO otherUser = UserUtil.createUser(client, otherOwner);
        ApiClient otherUserClient = ApiClients.basic(otherUser.getUsername(), otherUser.getPassword());

        ConsumerDTO consumer = Consumers.random(otherOwner);
        consumer = consumerApi.createConsumer(consumer, otherUser.getUsername(), otherOwner.getKey(), null,
            true);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerApi = consumerClient.consumers();
        AsyncJobStatusDTO bindStatus = AsyncJobStatusDTO.fromJson(consumerApi.bind(consumer.getUuid(),
            null, List.of(product.getId()), null, null, null, true, null, null));
        final AsyncJobStatusDTO finalStatus = jobsClient.waitForJob(bindStatus);
        assertThatJob(finalStatus).isFinished();

        assertForbidden(() -> userClient.jobs().getJobStatus(finalStatus.getId()));
    }

    @Test
    public void shouldAllowConsumerToViewStatusOfOwnJob() throws Exception {
        ConsumerDTO consumer = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerApi = consumerClient.consumers();
        AsyncJobStatusDTO bindStatus = AsyncJobStatusDTO.fromJson(consumerApi.bind(consumer.getUuid(),
            null, List.of(product.getId()), null, null, null, true, null, null));
        AsyncJobStatusDTO resultStatus = consumerClient.jobs().getJobStatus(bindStatus.getId());
        assertEquals(bindStatus.getId(), resultStatus.getId());

        // wait for job to complete, or test clean up will conflict with the asynchronous job.
        jobsClient.waitForJob(bindStatus);
    }

    @Test
    public void shouldNotAllowConsumerToAccessStatusOfOthersJob() throws Exception {
        ConsumerDTO consumer1 = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        ConsumerApi consumerApi1 = consumerClient1.consumers();
        AsyncJobStatusDTO bindStatus = AsyncJobStatusDTO.fromJson(consumerApi1.bind(consumer1.getUuid(),
            null, List.of(product.getId()), null, null, null, true, null, null));

        // wait for job to complete, or test clean up will conflict with the asynchronous job.
        jobsClient.waitForJob(bindStatus);

        ConsumerDTO consumer2 = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);

        assertForbidden(() -> consumerClient2.jobs().getJobStatus(bindStatus.getId()));
    }

    @Test
    public void shouldAllowConsumerToCancelOwnJob() throws Exception {
        jobsClient.setSchedulerStatus(false);
        try {
            ConsumerDTO consumer = client.consumers().createConsumer(Consumers.random(owner));
            ApiClient consumerClient = ApiClients.ssl(consumer);
            consumerApi = consumerClient.consumers();
            AsyncJobStatusDTO bindStatus = AsyncJobStatusDTO.fromJson(consumerApi.bind(consumer.getUuid(),
                null, List.of(product.getId()), null, null, null, true, null, null));

            consumerClient.jobs().cancelJob(bindStatus.getId());
        }
        finally {
            jobsClient.setSchedulerStatus(true);
        }
    }

    @Test
    public void shouldFailToCancelTerminalJob() throws Exception {
        ConsumerDTO consumer = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerApi = consumerClient.consumers();
        AsyncJobStatusDTO bindStatus = AsyncJobStatusDTO.fromJson(consumerApi.bind(consumer.getUuid(),
            null, List.of(product.getId()), null, null, null, true, null, null));
        jobsClient.waitForJob(bindStatus);

        assertBadRequest(() -> consumerClient.jobs().cancelJob(bindStatus.getId()));
    }

    @Test
    public void shouldNotAllowConsumerToCancelOthersJob() throws Exception {
        ConsumerDTO consumer1 = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        ConsumerApi consumerApi1 = consumerClient1.consumers();
        AsyncJobStatusDTO bindStatus = AsyncJobStatusDTO.fromJson(consumerApi1.bind(consumer1.getUuid(),
            null, List.of(product.getId()), null, null, null, true, null, null));

        // wait for job to complete, or test clean up will conflict with the asynchronous job.
        jobsClient.waitForJob(bindStatus);

        ConsumerDTO consumer2 = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);

        assertForbidden(() -> consumerClient2.jobs().cancelJob(bindStatus.getId()));
    }
}
