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
package org.candlepin.resource;


import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProduct;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;



/**
 * OwnerProductResourceTest
 */
public class OwnerProductResourceTest extends DatabaseTestFixture {

    @Inject protected ProductManager productManager;

    private OwnerProductResource ownerProductResource;

    @Before
    public void setup() {
        this.ownerProductResource = new OwnerProductResource(this.config, this.i18n, this.ownerCurator,
            this.ownerContentCurator, this.ownerProductCurator, this.productCertificateCurator,
            this.productCurator, this.productManager
        );
    }

    private ProductData buildTestProductDTO() {
        ProductData dto = TestUtil.createProductDTO("test_product");

        dto.setAttribute(Product.Attributes.VERSION, "1.0");
        dto.setAttribute(Product.Attributes.VARIANT, "server");
        dto.setAttribute(Product.Attributes.TYPE, "SVC");
        dto.setAttribute(Product.Attributes.ARCHITECTURE, "ALL");

        return dto;
    }

    private Product buildTestProduct() {
        Product entity = TestUtil.createProduct("test_product");

        entity.setAttribute(Product.Attributes.VERSION, "1.0");
        entity.setAttribute(Product.Attributes.VARIANT, "server");
        entity.setAttribute(Product.Attributes.TYPE, "SVC");
        entity.setAttribute(Product.Attributes.ARCHITECTURE, "ALL");

        return entity;
    }

    @Test
    public void testCreateProductResource() {
        Owner owner = this.createOwner("Example-Corporation");
        ProductData productData = this.buildTestProductDTO();

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), productData.getId()));

        ProductData result = this.ownerProductResource.createProduct(owner.getKey(), productData);
        Product entity = this.ownerProductCurator.getProductById(owner, productData.getId());

        assertNotNull(result);
        assertNotNull(entity);
        assertFalse(entity.isChangedBy(result));
    }

    @Test
    public void testCreateProductWithContent() {
        Owner owner = this.createOwner("Example-Corporation");
        Content content = this.createContent("content-1", "content-1", owner);
        ProductData productData = this.buildTestProductDTO();
        ContentData contentData = content.toDTO();
        productData.addContent(contentData, true);

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), productData.getId()));

        ProductData result = this.ownerProductResource.createProduct(owner.getKey(), productData);
        Product entity = this.ownerProductCurator.getProductById(owner, productData.getId());

        assertNotNull(result);
        assertNotNull(entity);
        assertFalse(entity.isChangedBy(result));

        assertNotNull(result.getProductContent());
        assertEquals(1, result.getProductContent().size());
        assertEquals(contentData, result.getProductContent().iterator().next().getContent());
    }

    @Test
    public void testUpdateProductWithoutId() {
        Owner owner = this.createOwner("Update-Product-Owner");
        ProductData productData = this.buildTestProductDTO();

        ProductData product = this.ownerProductResource.createProduct(owner.getKey(), productData);
        ProductData update = new ProductData();
        update.setName(product.getName());
        update.setAttribute("attri", "bute");
        ProductData result = this.ownerProductResource.updateProduct(owner.getKey(), product.getId(), update);
        assertEquals("bute", result.getAttributeValue("attri"));
    }

    @Test(expected = BadRequestException.class)
    public void testUpdateProductIdMismatch() {
        Owner owner = this.createOwner("Update-Product-Owner");
        ProductData productData = this.buildTestProductDTO();

        ProductData product = this.ownerProductResource.createProduct(owner.getKey(), productData);
        ProductData update = this.buildTestProductDTO();
        update.setId("TaylorSwift");
        ProductData result = this.ownerProductResource.updateProduct(owner.getKey(), product.getId(), update);
    }

    @Test(expected = BadRequestException.class)
    public void testDeleteProductWithSubscriptions() {
        OwnerCurator oc = mock(OwnerCurator.class);
        OwnerProductCurator opc = mock(OwnerProductCurator.class);
        ProductCurator pc = mock(ProductCurator.class);
        OwnerProduct op = mock(OwnerProduct.class);
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        OwnerProductResource pr = new OwnerProductResource(config, i18n, oc, null, opc, null, pc, null);

        Owner o = mock(Owner.class);
        Product p = mock(Product.class);
        op.setOwner(o);
        op.setProduct(p);

        when(oc.lookupByKey(eq("owner"))).thenReturn(o);
        when(opc.getProductById(eq(o), eq("10"))).thenReturn(p);
        when(opc.lockOwnerProductRelation(any(String.class), any(String.class))).thenReturn(true);
        when(opc.getOwnerProductByProductId(any(Owner.class), any(String.class))).thenReturn(op);
        when(op.getOwner()).thenReturn(o);
        when(op.getProduct()).thenReturn(p);
        when(p.isLocked()).thenReturn(false);

        Set<Subscription> subs = new HashSet<Subscription>();
        Subscription s = mock(Subscription.class);
        subs.add(s);
        when(pc.productHasSubscriptions(eq(o), eq(p))).thenReturn(true);

        pr.deleteProduct("owner", "10");
    }

    @Test(expected = ForbiddenException.class)
    public void testUpdateLockedProductFails() {
        Owner owner = this.createOwner("test_owner");
        Product product = this.createProduct("test_product", "test_product", owner);
        ProductData productData = TestUtil.createProductDTO("test_product", "updated_name");
        product.setLocked(true);
        this.productCurator.merge(product);

        assertNotNull(this.ownerProductCurator.getProductById(owner, productData.getId()));

        try {
            this.ownerProductResource.updateProduct(owner.getKey(), productData.getId(), productData);
        }
        catch (ForbiddenException e) {
            Product entity = this.ownerProductCurator.getProductById(owner, productData.getId());
            assertNotNull(entity);
            assertTrue(entity.isChangedBy(productData));

            throw e;
        }
    }

    @Test(expected = ForbiddenException.class)
    public void testDeleteLockedProductFails() {
        Owner owner = this.createOwner("test_owner");
        Product product = this.createProduct("test_product", "test_product", owner);
        product.setLocked(true);
        this.productCurator.merge(product);

        assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));

        try {
            this.ownerProductResource.deleteProduct(owner.getKey(), product.getId());
        }
        catch (ForbiddenException e) {
            assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));

            throw e;
        }
    }

    @Test
    public void getProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        Product entity = this.createProduct("test_product", "test_product", owner);

        securityInterceptor.enable();
        ProductData result = this.ownerProductResource.getProduct(owner.getKey(), entity.getId());

        assertNotNull(result);
        assertFalse(entity.isChangedBy(result));
    }

    @Test
    public void getProductCertificate() {
        Owner owner = this.createOwner("Example-Corporation");

        Product entity = this.createProduct(owner);
        // ensure we check SecurityHole
        securityInterceptor.enable();

        ProductCertificate cert = new ProductCertificate();
        cert.setCert("some text");
        cert.setKey("some key");
        cert.setProduct(entity);
        productCertificateCurator.create(cert);

        ProductCertificate cert1 = ownerProductResource.getProductCertificate(owner.getKey(), entity.getId());

        assertEquals(cert, cert1);
    }
}
