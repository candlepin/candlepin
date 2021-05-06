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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;



/**
 * Test suite for the RefreshPoolsForProductJob class
 */
public class RefreshPoolsForProductJobTest {

    private static final String VALID_ID = "valid_id";
    private static final String VALID_NAME = "valid_name";
    private static final String INVALID_ID = "";
    private ProductCurator productCurator;
    private PoolManager poolManager;
    private ProductServiceAdapter prodAdapter;
    private SubscriptionServiceAdapter subAdapter;

    @BeforeEach
    public void setUp() {
        productCurator = mock(ProductCurator.class);
        poolManager = mock(PoolManager.class);
        subAdapter = mock(SubscriptionServiceAdapter.class);
        prodAdapter = mock(ProductServiceAdapter.class);
    }

    @Test
    public void shouldSucceed() throws Exception {
        final String expected = "Pools refreshed for product: " + VALID_ID + "\n";
        final AsyncJob job = new RefreshPoolsForProductJob(productCurator, poolManager, subAdapter,
            prodAdapter);
        final Product product = new Product(INVALID_ID, VALID_NAME);
        product.setUuid(VALID_ID);
        final JobConfig jobConfig = RefreshPoolsForProductJob.createJobConfig()
            .setProduct(product)
            .setLazy(false);
        final JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(jobConfig.getJobArguments()).when(context).getJobArguments();
        doReturn(product).when(productCurator).get(eq(VALID_ID));

        Refresher mockRefresher = mock(Refresher.class);
        doReturn(mockRefresher).when(poolManager).getRefresher(any(), any(), anyBoolean());
        doReturn(mockRefresher).when(mockRefresher).add(any(Product.class));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertEquals(expected, result);
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
        final AsyncJob job = new RefreshPoolsForProductJob(productCurator, poolManager, subAdapter,
            prodAdapter);
        final Product product = new Product(INVALID_ID, VALID_NAME);
        final JobConfig jobConfig = RefreshPoolsForProductJob.createJobConfig()
            .setProduct(product)
            .setLazy(false);
        final JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(jobConfig.getJobArguments()).when(context).getJobArguments();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertEquals(
            "Unable to refresh pools for product \"null\": Could not find a product with the specified UUID",
            result);
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
