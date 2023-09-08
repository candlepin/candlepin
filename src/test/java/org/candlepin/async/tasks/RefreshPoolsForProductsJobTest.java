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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.Refresher;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class RefreshPoolsForProductsJobTest {

    private static final String VALID_ID = "valid_id";
    private static final String VALID_NAME = "valid_name";
    private static final String INVALID_ID = "";

    private ProductCurator productCurator;
    private ProductServiceAdapter prodAdapter;
    private SubscriptionServiceAdapter subAdapter;
    private RefresherFactory refresherFactory;

    @BeforeEach
    public void setUp() {
        productCurator = mock(ProductCurator.class);
        subAdapter = mock(SubscriptionServiceAdapter.class);
        prodAdapter = mock(ProductServiceAdapter.class);
        refresherFactory = mock(RefresherFactory.class);
    }

    private Product buildProduct() {
        return new Product()
            .setUuid("product_uuid")
            .setId(VALID_ID)
            .setName(VALID_NAME);
    }

    @Test
    public void shouldSucceed() throws Exception {
        final String expected = "Pools refreshed for products: [" + VALID_ID + "]\n";

        final Product product = this.buildProduct();

        doAnswer(iom -> {
            Map<String, Product> output = new HashMap<>();

            Collection<String> productIds = iom.getArgument(0);
            if (productIds != null && productIds.contains(product.getId())) {
                output.put(product.getId(), product);
            }

            return output;
        }).when(this.productCurator).getProductsByIds(any(Collection.class));

        doReturn(product).when(productCurator).get(eq(VALID_ID));

        final JobConfig jobConfig = RefreshPoolsForProductsJob.createJobConfig()
            .setProducts(List.of(product))
            .setLazy(false);

        final JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(jobConfig.getJobArguments()).when(context).getJobArguments();

        Refresher mockRefresher = mock(Refresher.class);
        doReturn(mockRefresher).when(this.refresherFactory).getRefresher(any(), any());
        doReturn(mockRefresher).when(mockRefresher).add(any(Product.class));
        doReturn(mockRefresher).when(mockRefresher).addProducts(any(Collection.class));
        doReturn(mockRefresher).when(mockRefresher).setLazyCertificateRegeneration(anyBoolean());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        AsyncJob job = new RefreshPoolsForProductsJob(productCurator, subAdapter,
            prodAdapter, refresherFactory);

        job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertEquals(expected, result);
    }

    @Test
    public void productAndLazyFlagMustBePresent() {
        final Product product = this.buildProduct();

        final JobConfig jobConfig = RefreshPoolsForProductsJob.createJobConfig()
            .setProducts(List.of(product))
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
        final Product product = new Product()
            .setId("invalid_id")
            .setName("product_name");

        final AsyncJob job = new RefreshPoolsForProductsJob(productCurator, subAdapter,
            prodAdapter, refresherFactory);
        final JobConfig jobConfig = RefreshPoolsForProductsJob.createJobConfig()
            .setProducts(List.of(product))
            .setLazy(false);
        final JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(jobConfig.getJobArguments()).when(context).getJobArguments();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertEquals("No products found for the given product IDs: [" + product.getId() + "]", result);
    }

    @Test
    public void productMustBePresent() {
        final JobConfig jobConfig = RefreshPoolsForProductsJob.createJobConfig()
            .setLazy(false);

        assertThrows(JobConfigValidationException.class, jobConfig::validate);
    }

    @Test
    public void productIdsMustBePresent() {
        final Product product = new Product();

        final JobConfig jobConfig = RefreshPoolsForProductsJob.createJobConfig()
            .setProducts(List.of(product))
            .setLazy(false);

        assertThrows(JobConfigValidationException.class, jobConfig::validate);
    }

    @Test
    public void lazyFlagMustBePresent() {
        final Product product = this.buildProduct();

        final JobConfig jobConfig = RefreshPoolsForProductsJob.createJobConfig()
            .setProducts(List.of(product));

        assertThrows(JobConfigValidationException.class, jobConfig::validate);
    }

}
