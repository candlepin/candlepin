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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Content;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Test;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;



/**
 * PopulateHostedDBTaskTest
 */
public class PopulateHostedDBTaskTest extends DatabaseTestFixture {

    @Test
    @SuppressWarnings("checkstyle:methodlength")
    public void testExecuteFullUpdate() throws Exception {
        // Setup
        JobExecutionContext jec = mock(JobExecutionContext.class);

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(PopulateHostedDBTask.SKIP_EXISTING, false);

        when(jec.getMergedJobDataMap()).thenReturn(jobDataMap);

        PoolCurator poolCuratorSpy = spy(this.poolCurator);
        ProductServiceAdapter psa = mock(ProductServiceAdapter.class);

        HashSet<String> productIds = new HashSet<String>();
        productIds.add("prod1");
        productIds.add("prod2");
        productIds.add("prod3");

        HashSet<String> dependentProductIds1 = new HashSet<String>();
        dependentProductIds1.add("dprod1");
        dependentProductIds1.add("dprod2");
        dependentProductIds1.add("prod3");

        HashSet<String> dependentProductIds1b = new HashSet<String>();
        dependentProductIds1b.add("dprod1");
        dependentProductIds1b.add("dprod2");

        HashSet<String> dependentProductIds2 = new HashSet<String>();
        dependentProductIds2.add("dprod3");
        dependentProductIds2.add("dprod4");
        dependentProductIds2.add("dprod2");

        HashSet<String> dependentProductIds2b = new HashSet<String>();
        dependentProductIds2b.add("dprod3");
        dependentProductIds2b.add("dprod4");

        // Unresolvable product references:
        productIds.add("unresolved_prod-1");
        productIds.add("unresolved_prod-2");
        productIds.add("unresolved_prod-3");

        when(poolCuratorSpy.getAllKnownProductIds()).thenReturn(productIds);

        Product p1 = TestUtil.createProduct("prod1", "prod1");
        p1.addContent(TestUtil.createContent("p1-content-1"));
        Product p2 = TestUtil.createProduct("prod2", "prod2");
        p2.addContent(TestUtil.createContent("p2-content-1"));
        p2.addContent(TestUtil.createContent("p2-content-2"));
        p2.setDependentProductIds(dependentProductIds1);
        Product p3 = TestUtil.createProduct("prod3", "prod3");
        p3.addContent(TestUtil.createContent("p3-content-1"));
        p3.addContent(TestUtil.createContent("p3-content-2"));
        p3.addContent(TestUtil.createContent("p3-content-3"));

        Product dp1 = TestUtil.createProduct("dprod1", "dprod1");
        dp1.addContent(TestUtil.createContent("dp1-content-1"));
        Product dp2 = TestUtil.createProduct("dprod2", "dprod2");
        dp2.addContent(TestUtil.createContent("dp2-content-1"));
        dp2.setDependentProductIds(dependentProductIds2);
        Product dp3 = TestUtil.createProduct("dprod3", "dprod3");
        dp3.addContent(TestUtil.createContent("dp3-content-1"));
        dp3.addContent(TestUtil.createContent("dp3-content-2"));
        Product dp4 = TestUtil.createProduct("dprod4", "dprod4");
        dp4.addContent(TestUtil.createContent("dp4-content-1"));
        dp4.addContent(TestUtil.createContent("dp4-content-2"));

        final HashMap<String, Product> productMap = new HashMap<String, Product>();
        productMap.put(p1.getId(), p1);
        productMap.put(p2.getId(), p2);
        productMap.put(p3.getId(), p3);
        productMap.put(dp1.getId(), dp1);
        productMap.put(dp2.getId(), dp2);
        productMap.put(dp3.getId(), dp3);
        productMap.put(dp4.getId(), dp4);

        LinkedList<Product> products = new LinkedList<Product>();
        products.add(p1);
        products.add(p2);
        products.add(p3);

        LinkedList<Product> dependentProducts1 = new LinkedList<Product>();
        dependentProducts1.add(dp1);
        dependentProducts1.add(dp2);
        dependentProducts1.add(p3);

        LinkedList<Product> dependentProducts1b = new LinkedList<Product>();
        dependentProducts1b.add(dp1);
        dependentProducts1b.add(dp2);

        LinkedList<Product> dependentProducts2 = new LinkedList<Product>();
        dependentProducts2.add(dp3);
        dependentProducts2.add(dp4);
        dependentProducts2.add(dp2);

        LinkedList<Product> dependentProducts2b = new LinkedList<Product>();
        dependentProducts2b.add(dp3);
        dependentProducts2b.add(dp4);

        when(psa.getProductsByIds(any(Collection.class))).thenAnswer(new Answer<List<Product>>() {
            @Override
            public List<Product> answer(InvocationOnMock iom) throws Throwable {
                Collection<String> productIds = (Collection<String>) iom.getArguments()[0];
                LinkedList<Product> output = new LinkedList<Product>();

                for (String pid : productIds) {
                    Product product = productMap.get(pid);

                    if (product != null) {
                        output.add(product);
                    }
                }

                return output;
            }
        });

        assertEquals(0, this.productCurator.listAll().size());
        assertEquals(0, this.contentCurator.listAll().size());

        CandlepinCommonTestConfig config = new CandlepinCommonTestConfig();
        config.setProperty(ConfigProperties.STANDALONE, "false");

        // Test
        PopulateHostedDBTask task = new PopulateHostedDBTask(
            psa, productCurator, this.contentCurator, poolCuratorSpy, config
        );

        task.execute(jec);

        // Verify
        verify(jec).setResult(eq("Finished populating hosted DB. Received 7 product(s) and 12 content " +
            "with 3 unresolved reference(s)"));

        for (Entry<String, Product> entry : productMap.entrySet()) {
            Product existing = this.productCurator.find(entry.getKey());

            assertNotNull("Product database entry missing for product id: " + entry.getKey(), existing);
            assertEquals("Product mismatch for product id: " + entry.getKey(), entry.getValue(), existing);

            for (ProductContent pc : entry.getValue().getProductContent()) {
                Content expected = pc.getContent();
                Content content = this.contentCurator.find(expected.getId());

                assertNotNull("Content database entry missing for content id: " + expected.getId(), content);
                assertEquals("Content mismatch for product id: " + expected.getId(), expected, content);
            }
        }

        assertEquals(7, this.productCurator.listAll().size());
        assertEquals(12, this.contentCurator.listAll().size());
    }

    @Test
    public void testExecuteSkipExisting() throws Exception {
        // Setup
        JobExecutionContext jec = mock(JobExecutionContext.class);

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(PopulateHostedDBTask.SKIP_EXISTING, true);

        when(jec.getMergedJobDataMap()).thenReturn(jobDataMap);

        PoolCurator poolCuratorSpy = spy(this.poolCurator);
        ProductServiceAdapter psa = mock(ProductServiceAdapter.class);

        HashSet<String> productIds = new HashSet<String>();
        productIds.add("prod1");
        productIds.add("prod2");
        productIds.add("prod3");
        productIds.add("prod4");
        productIds.add("prod5");

        when(poolCuratorSpy.getAllKnownProductIds()).thenReturn(productIds);

        Product p1 = TestUtil.createProduct("prod1", "prod1");
        p1.addContent(TestUtil.createContent("p1-content-1"));
        Product p2 = TestUtil.createProduct("prod2", "prod2");
        Product p3 = TestUtil.createProduct("prod3", "prod3");
        p3.addContent(TestUtil.createContent("p3-content-1"));
        p3.addContent(TestUtil.createContent("p3-content-2"));
        Product p4 = TestUtil.createProduct("prod4", "prod4");
        Product p5 = TestUtil.createProduct("prod5", "prod5");
        p5.addContent(TestUtil.createContent("p5-content-1"));
        p5.addContent(TestUtil.createContent("p5-content-2"));
        p5.addContent(TestUtil.createContent("p5-content-3"));

        this.productCurator.create(p2);
        this.productCurator.create(p4);

        final HashMap<String, Product> productMap = new HashMap<String, Product>();
        productMap.put(p1.getId(), p1);
        productMap.put(p2.getId(), p2);
        productMap.put(p3.getId(), p3);
        productMap.put(p4.getId(), p4);
        productMap.put(p5.getId(), p5);

        when(psa.getProductsByIds(any(Collection.class))).thenAnswer(new Answer<List<Product>>() {
            @Override
            public List<Product> answer(InvocationOnMock iom) throws Throwable {
                Collection<String> productIds = (Collection<String>) iom.getArguments()[0];
                LinkedList<Product> output = new LinkedList<Product>();

                for (String pid : productIds) {
                    Product product = productMap.get(pid);

                    if (product != null) {
                        output.add(product);
                    }
                }

                return output;
            }
        });

        assertEquals(2, this.productCurator.listAll().size());
        assertEquals(0, this.contentCurator.listAll().size());

        CandlepinCommonTestConfig config = new CandlepinCommonTestConfig();
        config.setProperty(ConfigProperties.STANDALONE, "false");

        // Test
        PopulateHostedDBTask task = new PopulateHostedDBTask(
            psa, productCurator, this.contentCurator, poolCuratorSpy, config
        );

        task.execute(jec);

        // Verify
        verify(jec).setResult(eq("Finished populating hosted DB. Received 3 product(s) and 6 content " +
            "with 0 unresolved reference(s)"));

        for (Entry<String, Product> entry : productMap.entrySet()) {
            Product existing = this.productCurator.find(entry.getKey());

            assertNotNull("Product database entry missing for product id: " + entry.getKey(), existing);
            assertEquals("Product mismatch for product id: " + entry.getKey(), entry.getValue(), existing);

            for (ProductContent pc : entry.getValue().getProductContent()) {
                Content expected = pc.getContent();
                Content content = this.contentCurator.find(expected.getId());

                assertNotNull("Content database entry missing for content id: " + expected.getId(), content);
                assertEquals("Content mismatch for product id: " + expected.getId(), expected, content);
            }
        }

        assertEquals(5, this.productCurator.listAll().size());
        assertEquals(6, this.contentCurator.listAll().size());
    }

    @Test
    public void ensureFailureOnInvalidProductAdapterBatchConfig() throws Exception {
        JobExecutionContext jec = mock(JobExecutionContext.class);
        CandlepinCommonTestConfig config = new CandlepinCommonTestConfig();
        config.setProperty(ConfigProperties.STANDALONE, "false");
        config.setProperty(ConfigProperties.POPULATE_HOSTED_DB_JOB_PROD_LOOKUP_BATCH_SIZE, "-1");

        // Test
        PopulateHostedDBTask task = new PopulateHostedDBTask(null, null, null, null, config);
        task.toExecute(jec);

        String resultMsg = String.format("Aborting populate DB task. Invalid configuration setting for {0}.",
            ConfigProperties.POPULATE_HOSTED_DB_JOB_PROD_LOOKUP_BATCH_SIZE);

        verify(jec).setResult(eq(resultMsg));
    }
}
