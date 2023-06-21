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


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.test.CollectionMatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;



public class EntitleByProductsJobTest {

    private Consumer consumer;
    private Owner owner;
    private String consumerUuid;
    private Entitler entitler;

    @BeforeEach
    public void init() {
        consumerUuid = "49bd6a8f-e9f8-40cc-b8d7-86cafd687a0e";

        final ConsumerType ctype = new ConsumerType("system");
        ctype.setId("test-ctype");
        owner = new Owner()
            .setId("test-owner-id")
            .setKey("test-owner")
            .setDisplayName("test-owner");

        consumer = new Consumer()
            .setUuid(consumerUuid)
            .setName("Test Consumer")
            .setUsername("test-consumer")
            .setOwner(owner)
            .setType(ctype);

        entitler = mock(Entitler.class);
    }

    @Test
    void allMandatoryValuesPresent() {
        final Set<String> pids = Set.of("pid1", "pid2", "pid3");
        final List<String> fromPools = Collections.singletonList("pool_id1");

        final JobConfig config = EntitleByProductsJob.createConfig()
            .setConsumer(consumer)
            .setProductIds(pids)
            .setEntitleDate(new Date())
            .setPools(fromPools);

        assertDoesNotThrow(config::validate);
    }

    @Test
    void consumerMustBePresent() {
        final Set<String> pids = Set.of("pid1", "pid2", "pid3");
        final List<String> fromPools = Collections.singletonList("pool_id1");

        final JobConfig config = EntitleByProductsJob.createConfig()
            .setProductIds(pids)
            .setEntitleDate(new Date())
            .setPools(fromPools);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    void consumerCannotBeBlank() {
        final Set<String> pids = Set.of("pid1", "pid2", "pid3");
        final List<String> fromPools = Collections.singletonList("pool_id1");
        consumer.setUuid("");

        final JobConfig config = EntitleByProductsJob.createConfig()
            .setConsumer(consumer)
            .setProductIds(pids)
            .setEntitleDate(new Date())
            .setPools(fromPools);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    void productIdsCanBeMissing() {
        final List<String> fromPools = Collections.singletonList("pool_id1");

        final JobConfig config = EntitleByProductsJob.createConfig()
            .setConsumer(consumer)
            .setEntitleDate(new Date())
            .setPools(fromPools);

        assertDoesNotThrow(config::validate);
    }

    @Test
    void productIdsCanBeEmpty() {
        final Set<String> pids = Set.of();
        final List<String> fromPools = Collections.singletonList("pool_id1");

        final JobConfig config = EntitleByProductsJob.createConfig()
            .setConsumer(consumer)
            .setProductIds(pids)
            .setEntitleDate(new Date())
            .setPools(fromPools);

        assertDoesNotThrow(config::validate);
    }

    @Test
    void entitleDateIsOptional() {
        final Set<String> pids = Set.of("pid1", "pid2", "pid3");
        final List<String> fromPools = Collections.singletonList("pool_id1");

        final JobConfig config = EntitleByProductsJob.createConfig()
            .setConsumer(consumer)
            .setProductIds(pids)
            .setPools(fromPools);

        assertDoesNotThrow(config::validate);
    }

    @Test
    void fromPoolsMustBePresent() {
        final Set<String> pids = Set.of("pid1", "pid2", "pid3");

        final JobConfig config = EntitleByProductsJob.createConfig()
            .setConsumer(consumer)
            .setProductIds(pids)
            .setEntitleDate(new Date());

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    void bindByProductsExec() throws Exception  {
        final Set<String> pids = Set.of("pid1", "pid2", "pid3");
        CollectionMatcher<String> matcher = new CollectionMatcher<>(pids);

        final List<String> fromPools = Collections.singletonList("pool_id1");
        final Date entitleDate = new Date();
        final JobConfig config = EntitleByProductsJob.createConfig()
            .setConsumer(consumer)
            .setProductIds(pids)
            .setEntitleDate(entitleDate)
            .setPools(fromPools);
        final JobExecutionContext ctx = mock(JobExecutionContext.class);
        final List<Entitlement> ents = Arrays.asList(mock(Entitlement.class), mock(Entitlement.class));
        when(ctx.getJobArguments()).thenReturn(config.getJobArguments());
        when(entitler.bindByProducts(argThat(matcher), eq(consumerUuid), eq(entitleDate), eq(fromPools)))
            .thenReturn(ents);
        final EntitleByProductsJob job = new EntitleByProductsJob(entitler);

        job.execute(ctx);

        verify(entitler).bindByProducts(argThat(matcher), eq(consumerUuid), eq(entitleDate), eq(fromPools));
        verify(entitler).sendEvents(eq(ents));
    }

}
