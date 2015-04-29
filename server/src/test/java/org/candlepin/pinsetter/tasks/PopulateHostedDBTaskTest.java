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

import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.Test;
import org.quartz.JobExecutionContext;

import java.util.HashSet;
import java.util.LinkedList;



/**
 * PopulateHostedDBTaskTest
 */
public class PopulateHostedDBTaskTest {

    @Test
    public void testExecute() throws Exception {
        // Setup
        JobExecutionContext jec = mock(JobExecutionContext.class);

        ContentCurator contentCurator = mock(ContentCurator.class);
        PoolCurator poolCurator = mock(PoolCurator.class);
        ProductCurator productCurator = mock(ProductCurator.class);
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

        when(poolCurator.getAllKnownProductIds()).thenReturn(productIds);

        Product p1 = TestUtil.createProduct("prod1", "prod1");
        p1.addContent(new Content());
        Product p2 = TestUtil.createProduct("prod2", "prod2");
        p2.addContent(new Content());
        p2.addContent(new Content());
        p2.setDependentProductIds(dependentProductIds1);
        Product p3 = TestUtil.createProduct("prod3", "prod3");
        p3.addContent(new Content());
        p3.addContent(new Content());
        p3.addContent(new Content());

        Product dp1 = TestUtil.createProduct("dprod1", "dprod1");
        dp1.addContent(new Content());
        Product dp2 = TestUtil.createProduct("dprod2", "dprod2");
        dp2.addContent(new Content());
        dp2.setDependentProductIds(dependentProductIds2);
        Product dp3 = TestUtil.createProduct("dprod3", "dprod3");
        dp3.addContent(new Content());
        dp3.addContent(new Content());
        Product dp4 = TestUtil.createProduct("dprod4", "dprod4");
        dp4.addContent(new Content());
        dp4.addContent(new Content());

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

        when(psa.getProductsByIds(productIds)).thenReturn(products);
        when(psa.getProductsByIds(dependentProductIds1)).thenReturn(dependentProducts1);
        when(psa.getProductsByIds(dependentProductIds1b)).thenReturn(dependentProducts1b);
        when(psa.getProductsByIds(dependentProductIds2)).thenReturn(dependentProducts2);
        when(psa.getProductsByIds(dependentProductIds2b)).thenReturn(dependentProducts2b);


        // Test
        PopulateHostedDBTask task = new PopulateHostedDBTask(
            psa, productCurator, contentCurator, poolCurator
        );

        task.execute(jec);

        // Verify
        verify(productCurator, times(7)).createOrUpdate(any(Product.class));
        verify(contentCurator, times(12)).createOrUpdate(any(Content.class));

        verify(jec).setResult(
            eq("Finished populating Hosted DB. Received 7 product(s) and 12 content")
        );
    }

}
