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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.resteasy.IterableStreamingOutputFactory;
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

        dto.setAttribute("version", "1.0");
        dto.setAttribute("variant", "server");
        dto.setAttribute("type", "SVC");
        dto.setAttribute("arch", "ALL");

        return dto;
    }

    private Product buildTestProduct() {
        Product entity = TestUtil.createProduct("test_product");

        entity.setAttribute("version", "1.0");
        entity.setAttribute("variant", "server");
        entity.setAttribute("type", "SVC");
        entity.setAttribute("arch", "ALL");

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

    @Test(expected = BadRequestException.class)
    public void testDeleteProductWithSubscriptions() {
        OwnerCurator oc = mock(OwnerCurator.class);
        OwnerProductCurator opc = mock(OwnerProductCurator.class);
        ProductCurator pc = mock(ProductCurator.class);
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        OwnerProductResource pr = new OwnerProductResource(
            config, i18n, null, oc, null, opc, null, pc, null
        );

        Owner o = mock(Owner.class);
        Product p = mock(Product.class);

        when(oc.lookupByKey(eq("owner"))).thenReturn(o);
        when(opc.getProductById(eq(o), eq("10"))).thenReturn(p);

        Set<Subscription> subs = new HashSet<Subscription>();
        Subscription s = mock(Subscription.class);
        subs.add(s);
        when(pc.productHasSubscriptions(eq(p), eq(o))).thenReturn(true);

        pr.deleteProduct("owner", "10");
    }

    @Test
    public void getProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        Product entity = this.createProduct("test_product", "test_product", owner);

        securityInterceptor.enable();
        ProductData result = ownerProductResource.getProduct(owner.getKey(), entity.getId());

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
