/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RefreshPoolsForProductJobTest {

    private static final String VALID_ID = "valid_id";
    private static final String VALID_NAME = "valid_name";
    private static final String INVALID_ID = "";
    private ProductCurator productCurator;
    private PoolManager poolManager;
    private SubscriptionServiceAdapter subAdapter;

    @BeforeEach
    public void setUp() {
        productCurator = mock(ProductCurator.class);
        poolManager = mock(PoolManager.class);
        subAdapter = mock(SubscriptionServiceAdapter.class);
    }

    @Test
    public void shouldSucceed() throws Exception {
        final String expected = "Pools refreshed for product: " + VALID_ID + "\n";
        final AsyncJob job = new RefreshPoolsForProductJob(productCurator, poolManager, subAdapter);
        final Product product = new Product(INVALID_ID, VALID_NAME);
        product.setUuid(VALID_ID);
        final JobConfig jobConfig = RefreshPoolsForProductJob.createJobConfig()
            .setProduct(product)
            .setLazy(false);
        final JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(jobConfig.getJobArguments()).when(context).getJobArguments();
        doReturn(product).when(productCurator).get(eq(VALID_ID));
        doReturn(mock(Refresher.class)).when(poolManager).getRefresher(any(), anyBoolean());

        final Object actualResult = job.execute(context);

        assertEquals(expected, actualResult);
    }

    @Test
    public void productAndLazyFlagMustBePresent() {
        final Product product = new Product(INVALID_ID, VALID_NAME);
        product.setUuid(VALID_ID);

        final JobConfig jobConfig = RefreshPoolsForProductJob.createJobConfig()
            .setProduct(product)
            .setLazy(false);

        try {
            jobConfig.validate();
        }
        catch (JobConfigValidationException e) {
            fail("Validation should pass!");
        }
    }

    @Test
    public void shouldFailWhenProductNotFound() throws Exception {
        final AsyncJob job = new RefreshPoolsForProductJob(productCurator, poolManager, subAdapter);
        final Product product = new Product(INVALID_ID, VALID_NAME);
        final JobConfig jobConfig = RefreshPoolsForProductJob.createJobConfig()
            .setProduct(product)
            .setLazy(false);
        final JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(jobConfig.getJobArguments()).when(context).getJobArguments();

        final Object actualResult = job.execute(context);

        assertEquals(
            "Unable to refresh pools for product \"null\": Could not find a product with the specified UUID",
            actualResult);
    }

    @Test
    public void productMustBePresent() {
        final JobConfig jobConfig = RefreshPoolsForProductJob.createJobConfig()
            .setLazy(false);

        assertThrows(JobConfigValidationException.class, jobConfig::validate);
    }

    @Test
    public void productUuidMustBePresent() {
        final Product product = new Product(INVALID_ID, VALID_NAME);

        final JobConfig jobConfig = RefreshPoolsForProductJob.createJobConfig()
            .setProduct(product)
            .setLazy(false);

        assertThrows(JobConfigValidationException.class, jobConfig::validate);
    }

    @Test
    public void lazyFlagMustBePresent() {
        final Product product = new Product(VALID_ID, VALID_NAME);
        product.setUuid(VALID_ID);

        final JobConfig jobConfig = RefreshPoolsForProductJob.createJobConfig()
            .setProduct(product);

        assertThrows(JobConfigValidationException.class, jobConfig::validate);
    }

}
