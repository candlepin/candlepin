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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.JobExecutionContext;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Set;

/**
 * Test suite for the OrphanCleanupJob class
 */
public class OrphanCleanupJobTest extends DatabaseTestFixture {

    private OrphanCleanupJob createJobInstance() {
        return new OrphanCleanupJob(this.contentCurator, this.ownerContentCurator, this.productCurator,
            this.ownerProductCurator);
    }

    private Content createOrphanedContent() {
        String contentId = "test-content-" + TestUtil.randomInt();
        Content content = TestUtil.createContent(contentId, contentId);
        return this.contentCurator.create(content);
    }

    private Product createOrphanedProduct() {
        String productId = "test-product-" + TestUtil.randomInt();
        Product product = TestUtil.createProduct(productId, productId);
        return this.productCurator.create(product);
    }

    @Test
    public void testStandardExecution() throws Exception {
        Set<Owner> owners = new HashSet<>();

        for (int i = 0; i < 3; ++i) {
            Owner owner = this.createOwner();
            owners.add(owner);
        }

        Set<Content> existingContent = new HashSet<>();
        Set<Content> orphanedContent = new HashSet<>();
        Set<Product> existingProducts = new HashSet<>();
        Set<Product> orphanedProducts = new HashSet<>();

        for (Owner owner : owners) {
            for (int i = 0; i < 5; ++i) {
                Content content = this.createContent(owner);
                existingContent.add(content);

                Product product = this.createProduct(owner);
                existingProducts.add(product);
            }

            for (int i = 0; i < 2; ++i) {
                Content content = this.createOrphanedContent();
                orphanedContent.add(content);

                Product product = this.createOrphanedProduct();
                orphanedProducts.add(product);
            }
        }

        this.ownerCurator.flush();

        OrphanCleanupJob job = this.createJobInstance();
        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertThat(result, instanceOf(String.class)); // Should be a string with some log-like info

        // Verify all of the content and products for the various owners still exist
        this.ownerCurator.flush();
        this.ownerCurator.clear();

        for (Content existing : existingContent) {
            Content content = this.contentCurator.get(existing.getUuid());

            assertNotNull(content);
            assertEquals(existing, content);
        }

        for (Product existing : existingProducts) {
            Product product = this.productCurator.get(existing.getUuid());

            assertNotNull(product);
            assertEquals(existing, product);
        }

        // Verify all of the orphaned entities were removed
        for (Content orphan : orphanedContent) {
            Content content = this.contentCurator.get(orphan.getUuid());
            assertNull(content);
        }

        for (Product orphan : orphanedProducts) {
            Product product = this.productCurator.get(orphan.getUuid());
            assertNull(product);
        }
    }

    @Test
    public void testCleanupDoesNotRemoveOrphansReferencedByPools() throws Exception {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createOrphanedProduct();
        Product product2 = this.createOrphanedProduct();
        Product product3 = this.createOrphanedProduct();

        Pool pool1 = this.createPool(owner1, product1);
        Pool pool2 = this.createPool(owner2, product2);

        // Execute job
        OrphanCleanupJob job = this.createJobInstance();
        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = new JobExecutionContext(status);

        job.execute(context);

        this.ownerCurator.flush();
        this.ownerCurator.clear();

        // Verify referenced orphans were not removed
        assertNotNull(this.productCurator.get(product1.getUuid()));
        assertNotNull(this.productCurator.get(product2.getUuid()));

        // Verify unreferenced orphans were
        assertNull(this.productCurator.get(product3.getUuid()));
    }

    @Test
    public void testCleanupDoesNotRemoveOrphansReferencedByProducts() throws Exception {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createOrphanedProduct();
        Product product2 = this.createOrphanedProduct();
        Product product3 = this.createOrphanedProduct();

        Product refProduct1 = TestUtil.createProduct("ref_p1", "ref product 1");
        refProduct1.addProvidedProduct(product1);

        Product refProduct2 = TestUtil.createProduct("ref_p2", "ref product 2")
            .setDerivedProduct(product2);

        refProduct1 = this.createProduct(refProduct1, owner1);
        refProduct2 = this.createProduct(refProduct2, owner2);

        // Execute job
        OrphanCleanupJob job = this.createJobInstance();
        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = new JobExecutionContext(status);

        job.execute(context);

        this.ownerCurator.flush();
        this.ownerCurator.clear();

        // Verify referenced orphans were not removed
        assertNotNull(this.productCurator.get(product1.getUuid()));
        assertNotNull(this.productCurator.get(product2.getUuid()));

        // Verify unreferenced orphans were
        assertNull(this.productCurator.get(product3.getUuid()));
    }
}
