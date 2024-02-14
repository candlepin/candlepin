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


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.Entitler;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.dto.PoolIdAndErrors;
import org.candlepin.model.dto.PoolIdAndQuantity;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


public class EntitlerJobTest {

    private Consumer consumer;
    private Owner owner;
    private Entitler entitler;
    private PoolCurator poolCurator;
    private I18n i18n;

    private static final String CONSUMER_UUID = "49bd6a8f-e9f8-40cc-b8d7-86cafd687a0e";
    private static final String POOL_ID = "pool10";
    private static final int LIMIT = 5;

    @BeforeEach
    public void init() {

        final ConsumerType ctype = new ConsumerType("system");
        ctype.setId("test-ctype");

        owner = new Owner()
            .setId("test-owner-id")
            .setKey("test-owner")
            .setDisplayName("test-owner");

        consumer = new Consumer()
            .setUuid(CONSUMER_UUID)
            .setName("Test Consumer")
            .setUsername("test-consumer")
            .setOwner(owner)
            .setType(ctype);

        entitler = mock(Entitler.class);
        poolCurator = mock(PoolCurator.class);
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    @AfterEach
    void cleanup() {
        new File("obj.ser").delete();
    }

    @Test
    void allArgsOk() {
        final JobConfig config = EntitlerJob.createConfig(LIMIT)
            .setOwner(owner)
            .setConsumer(consumer)
            .setPoolQuantity(POOL_ID, 1);

        assertDoesNotThrow(config::validate);
    }

    @Test
    void consumerMustBePresent() {
        final JobConfig config = EntitlerJob.createConfig(LIMIT)
            .setOwner(owner)
            .setPoolQuantity(POOL_ID, 1);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    void consumerCannotBeBlank() {
        consumer.setUuid("");

        final JobConfig config = EntitlerJob.createConfig(LIMIT)
            .setOwner(owner)
            .setConsumer(consumer)
            .setPoolQuantity(POOL_ID, 1);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    void poolQuantitiesMustBePresent() {
        final JobConfig config = EntitlerJob.createConfig(LIMIT)
            .setOwner(owner)
            .setConsumer(consumer);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    void poolQuantityIdCannotBeBlank() {
        assertThrows(IllegalArgumentException.class,
            () -> EntitlerJob.createConfig(LIMIT).setPoolQuantity("", 1));
    }

    @Test
    void poolQuantitiesCannotNegative() {
        assertThrows(IllegalArgumentException.class,
            () -> EntitlerJob.createConfig(LIMIT).setPoolQuantity(POOL_ID, -1));
    }

    @Test
    public void bindByPoolExec() throws JobExecutionException, EntitlementRefusedException {
        final JobConfig config = createJobConfig();
        final JobExecutionContext context = createJobContext(config);
        final List<Entitlement> entitlements = createEntitlements();
        final EntitlerJob job = createEntitlerJob(poolCurator, i18n);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertThat(result, instanceOf(List.class));
        List<PoolIdAndQuantity> resultQuantities = (List<PoolIdAndQuantity>) result;

        verify(entitler).bindByPoolQuantities(eq(CONSUMER_UUID), anyMap());
        verify(entitler).sendEvents(eq(entitlements));
        assertEquals(1, resultQuantities.size());
        assertEquals(POOL_ID, resultQuantities.get(0).getPoolId());
        assertEquals(100, resultQuantities.get(0).getQuantity().intValue());
    }

    @Test
    public void handleException() throws EntitlementRefusedException {
        final JobConfig config = createJobConfig();
        final JobExecutionContext ctx = createJobContext(config);
        when(entitler.bindByPoolQuantities(eq(CONSUMER_UUID), anyMap()))
            .thenThrow(new ForbiddenException("job should fail"));
        final EntitlerJob job = createEntitlerJob(poolCurator, i18n);

        assertThrows(JobExecutionException.class, () -> job.execute(ctx));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void respondWithValidationErrors() throws JobExecutionException, EntitlementRefusedException {
        final JobConfig config = createJobConfig();
        final JobExecutionContext ctx = createJobContext(config);
        final String poolId = "hello";
        stubEntitlerErrorResult(poolId);
        stubPoolCuratorPoolsById();

        EntitlerJob job = createEntitlerJob(poolCurator, i18n);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        job.execute(ctx);

        verify(ctx, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertThat(result, instanceOf(List.class));
        List<PoolIdAndErrors> resultErrors = (List<PoolIdAndErrors>) result;

        assertEquals(1, resultErrors.size());
        assertEquals(poolId, resultErrors.get(0).getPoolId());
        assertEquals(1, resultErrors.get(0).getErrors().size());
        assertEquals(
            "No subscriptions are available from the pool with ID \"" + poolId + "\".",
            resultErrors.get(0).getErrors().get(0));
    }

    private EntitlerJob createEntitlerJob(final PoolCurator poolCurator, final I18n i18n) {
        return new EntitlerJob(entitler, poolCurator, i18n);
    }

    private void stubEntitlerErrorResult(String poolId) throws EntitlementRefusedException {
        final HashMap<String, ValidationResult> mapResult = new HashMap<>();
        final ValidationResult result = new ValidationResult();
        result.addError("rulefailed.no.entitlements.available");
        mapResult.put(poolId, result);
        when(entitler.bindByPoolQuantities(eq(CONSUMER_UUID), anyMap()))
            .thenThrow(new EntitlementRefusedException(mapResult));
    }

    private void stubPoolCuratorPoolsById() {
        final Pool pool = createPool("hello");
        final List<Pool> pools = List.of(pool);
        when(poolCurator.listAllByIds(anySet())).thenReturn(pools);
    }

    private JobExecutionContext createJobContext(final JobConfig config) {
        final JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getJobArguments()).thenReturn(config.getJobArguments());
        return ctx;
    }

    private JobConfig createJobConfig() {
        return EntitlerJob.createConfig(LIMIT)
            .setOwner(owner)
            .setConsumer(consumer)
            .setPoolQuantity(POOL_ID, 1);
    }

    private List<Entitlement> createEntitlements() throws EntitlementRefusedException {
        final Pool pool = createPool(POOL_ID);
        final Entitlement entitlement = new Entitlement();
        entitlement.setPool(pool);
        entitlement.setQuantity(100);
        final List<Entitlement> entitlements = List.of(entitlement);
        doReturn(entitlements)
            .when(entitler)
            .bindByPoolQuantities(eq(CONSUMER_UUID), anyMap());
        return entitlements;
    }

    private Pool createPool(final String poolId) {
        final Pool p = new Pool();
        p.setId(poolId);
        return p;
    }

}
