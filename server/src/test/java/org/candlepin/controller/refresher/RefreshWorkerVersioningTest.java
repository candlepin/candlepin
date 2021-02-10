/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller.refresher.visitors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.controller.refresher.RefreshResult;
import org.candlepin.controller.refresher.RefreshResult.EntityState;
import org.candlepin.controller.refresher.RefreshWorker;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;



/**
 * Test suite for the RefreshWorker class focusing specifically on entity versioning,
 * and the required converging and diverging functionality
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RefreshWorkerVersioningTest extends DatabaseTestFixture {

    @BeforeEach
    public void init() throws Exception {
        super.init();
    }

    private RefreshWorker buildRefreshWorker() {
        return new RefreshWorker(this.poolCurator, this.productCurator, this.ownerProductCurator,
            this.contentCurator, this.ownerContentCurator);
    }

    private ProductInfo mockProductInfo(String id, String name) {
        ProductInfo entity = mock(ProductInfo.class);
        doReturn(id).when(entity).getId();
        doReturn(null).when(entity).getMultiplier();
        doReturn(name).when(entity).getName();

        return entity;
    }

    private ContentInfo mockContentInfo(String id, String name) {
        ContentInfo entity = mock(ContentInfo.class);
        doReturn(id).when(entity).getId();
        doReturn(null).when(entity).getMetadataExpiration();
        doReturn(name).when(entity).getName();

        return entity;
    }

    @Test
    public void testProductCreationMergesWithExistingVersion() {
        Owner owner1 = this.createOwner("owner-1");
        Owner owner2 = this.createOwner("owner-2");

        String id = "pid-1";
        String name = "product_name";

        Product product = this.createProduct(id, name, owner1);

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addProducts(product);

        RefreshResult result = worker.execute(owner2);

        assertNotNull(result);
        assertEquals(1, result.getEntities(Product.class, EntityState.CREATED).size());
        assertEquals(0, result.getEntities(Product.class, EntityState.UPDATED).size());
        assertEquals(0, result.getEntities(Product.class, EntityState.UNCHANGED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.DELETED).size());

        Product created = result.getEntity(Product.class, id);

        assertNotNull(created);
        assertEquals(product, created);
        assertEquals(product.getUuid(), created.getUuid());
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(created, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(created, owner2));
    }

    @Test
    public void testProductUpdateMergesWithExistingVersion() {
        Owner owner1 = this.createOwner("owner-1");
        Owner owner2 = this.createOwner("owner-2");

        String id = "pid-1";
        String name = "product_name";

        Product product = this.createProduct(id, name, owner1);
        Product existing = this.createProduct(id, "old_name", owner2);
        ProductInfo imported = this.mockProductInfo(id, name);

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addProducts(imported);

        RefreshResult result = worker.execute(owner2);

        assertNotNull(result);
        assertEquals(0, result.getEntities(Product.class, EntityState.CREATED).size());
        assertEquals(1, result.getEntities(Product.class, EntityState.UPDATED).size());
        assertEquals(0, result.getEntities(Product.class, EntityState.UNCHANGED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.DELETED).size());

        Product updated = result.getEntity(Product.class, id);

        assertNotNull(updated);
        assertEquals(product, updated);
        assertEquals(product.getUuid(), updated.getUuid());
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(updated, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(updated, owner2));
    }

    @Test
    public void testProductUpdateDivergesFromExistingVersion() {
        Owner owner1 = this.createOwner("owner-1");
        Owner owner2 = this.createOwner("owner-2");

        String id = "pid-1";

        Product product = this.createProduct(id, "old_name", owner1, owner2);
        ProductInfo imported = this.mockProductInfo(id, "new_name");

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addProducts(imported);

        RefreshResult result = worker.execute(owner2);

        assertNotNull(result);
        assertEquals(0, result.getEntities(Product.class, EntityState.CREATED).size());
        assertEquals(1, result.getEntities(Product.class, EntityState.UPDATED).size());
        assertEquals(0, result.getEntities(Product.class, EntityState.UNCHANGED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.DELETED).size());

        Product updated = result.getEntity(Product.class, id);

        assertNotNull(updated);
        assertNotEquals(product, updated);
        assertNotEquals(product.getUuid(), updated.getUuid());
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(updated, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(updated, owner2));
    }


    @Test
    public void testContentCreationMergesWithExistingVersion() {
        Owner owner1 = this.createOwner("owner-1");
        Owner owner2 = this.createOwner("owner-2");

        String id = "pid-1";
        String name = "content_name";

        Content content = this.createContent(id, name, owner1);

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addContent(content);

        RefreshResult result = worker.execute(owner2);

        assertNotNull(result);
        assertEquals(1, result.getEntities(Content.class, EntityState.CREATED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.UPDATED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.UNCHANGED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.DELETED).size());

        Content created = result.getEntity(Content.class, id);

        assertNotNull(created);
        assertEquals(content, created);
        assertEquals(content.getUuid(), created.getUuid());
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(created, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(created, owner2));
    }

    @Test
    public void testContentUpdateMergesWithExistingVersion() {
        Owner owner1 = this.createOwner("owner-1");
        Owner owner2 = this.createOwner("owner-2");

        String id = "pid-1";
        String name = "content_name";

        Content content = this.createContent(id, name, owner1);
        Content existing = this.createContent(id, "old_name", owner2);
        ContentInfo imported = this.mockContentInfo(id, name);

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addContent(imported);

        RefreshResult result = worker.execute(owner2);

        assertNotNull(result);
        assertEquals(0, result.getEntities(Content.class, EntityState.CREATED).size());
        assertEquals(1, result.getEntities(Content.class, EntityState.UPDATED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.UNCHANGED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.DELETED).size());

        Content updated = result.getEntity(Content.class, id);

        assertNotNull(updated);
        assertEquals(content, updated);
        assertEquals(content.getUuid(), updated.getUuid());
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(updated, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(updated, owner2));
    }

    @Test
    public void testContentUpdateDivergesFromExistingVersion() {
        Owner owner1 = this.createOwner("owner-1");
        Owner owner2 = this.createOwner("owner-2");

        String id = "pid-1";

        Content content = this.createContent(id, "old_name", owner1, owner2);
        ContentInfo imported = this.mockContentInfo(id, "new_name");

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addContent(imported);

        RefreshResult result = worker.execute(owner2);

        assertNotNull(result);
        assertEquals(0, result.getEntities(Content.class, EntityState.CREATED).size());
        assertEquals(1, result.getEntities(Content.class, EntityState.UPDATED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.UNCHANGED).size());
        assertEquals(0, result.getEntities(Content.class, EntityState.DELETED).size());

        Content updated = result.getEntity(Content.class, id);

        assertNotNull(updated);
        assertNotEquals(content, updated);
        assertNotEquals(content.getUuid(), updated.getUuid());
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner2));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(updated, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(updated, owner2));
    }
}
