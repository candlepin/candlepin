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

import org.candlepin.async.JobManager;
import org.candlepin.common.resource.exceptions.BadRequestException;
import org.candlepin.common.resource.exceptions.ForbiddenException;
import org.candlepin.controller.ProductManager;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ProductCertificateDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



/**
 * OwnerProductResourceTest
 */
public class OwnerProductResourceTest extends DatabaseTestFixture {

    @Inject protected ProductManager productManager;

    private JobManager jobManager;

    private OwnerProductResource ownerProductResource;

    @BeforeEach
    public void setup() {
        this.jobManager = mock(JobManager.class);

        this.ownerProductResource = new OwnerProductResource(this.config, this.i18n, this.ownerCurator,
            this.ownerContentCurator, this.ownerProductCurator, this.productCertificateCurator,
            this.productCurator, this.productManager, this.modelTranslator, this.jobManager
        );
    }

    private ProductDTO buildTestProductDTO() {
        ProductDTO dto = TestUtil.createProductDTO("test_product");

        dto.setAttribute(Product.Attributes.VERSION, "1.0");
        dto.setAttribute(Product.Attributes.VARIANT, "server");
        dto.setAttribute(Product.Attributes.TYPE, "SVC");
        dto.setAttribute(Product.Attributes.ARCHITECTURE, "ALL");

        return dto;
    }

    @Test
    public void testCreateProductResource() {
        Owner owner = this.createOwner("Example-Corporation");
        ProductDTO pdto = this.buildTestProductDTO();

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), pdto.getId()));

        ProductDTO result = this.ownerProductResource.createProduct(owner.getKey(), pdto);
        Product entity = this.ownerProductCurator.getProductById(owner, pdto.getId());
        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);

        assertNotNull(result);
        assertNotNull(entity);
        assertEquals(expected, result);
    }

    @Test
    public void testCreateProductWithContent() {
        Owner owner = this.createOwner("Example-Corporation");
        Content content = this.createContent("content-1", "content-1", owner);
        ProductDTO pdto = this.buildTestProductDTO();
        ContentDTO cdto = this.modelTranslator.translate(content, ContentDTO.class);
        pdto.addContent(cdto, true);

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), pdto.getId()));

        ProductDTO result = this.ownerProductResource.createProduct(owner.getKey(), pdto);
        Product entity = this.ownerProductCurator.getProductById(owner, pdto.getId());
        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);

        assertNotNull(result);
        assertNotNull(entity);
        assertEquals(expected, result);

        assertNotNull(result.getProductContent());
        assertEquals(1, result.getProductContent().size());
        assertEquals(cdto, result.getProductContent().iterator().next().getContent());
    }

    @Test
    public void testUpdateProductWithoutId() {
        Owner owner = this.createOwner("Update-Product-Owner");
        ProductDTO pdto = this.buildTestProductDTO();

        ProductDTO product = this.ownerProductResource.createProduct(owner.getKey(), pdto);
        ProductDTO update = new ProductDTO();
        update.setName(product.getName());
        update.setAttribute("attri", "bute");
        ProductDTO result = this.ownerProductResource.updateProduct(owner.getKey(), product.getId(), update);
        assertEquals("bute", result.getAttributeValue("attri"));
    }

    @Test
    public void testUpdateProductIdMismatch() {
        Owner owner = this.createOwner("Update-Product-Owner");
        ProductDTO pdto = this.buildTestProductDTO();

        ProductDTO product = this.ownerProductResource.createProduct(owner.getKey(), pdto);
        ProductDTO update = this.buildTestProductDTO();
        update.setId("TaylorSwift");
        assertThrows(BadRequestException.class, () ->
            this.ownerProductResource.updateProduct(owner.getKey(), product.getId(), update)
        );
    }

    @Test
    public void testDeleteProductWithSubscriptions() {
        OwnerCurator oc = mock(OwnerCurator.class);
        OwnerProductCurator opc = mock(OwnerProductCurator.class);
        ProductCurator pc = mock(ProductCurator.class);
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        OwnerProductResource pr = new OwnerProductResource(
            config, i18n, oc, null, opc, null, pc, null, this.modelTranslator, this.jobManager);

        Owner o = mock(Owner.class);
        Product p = mock(Product.class);

        when(oc.getByKey(eq("owner"))).thenReturn(o);
        when(opc.getProductById(eq(o), eq("10"))).thenReturn(p);

        Set<Subscription> subs = new HashSet<>();
        Subscription s = mock(Subscription.class);
        subs.add(s);
        when(pc.productHasSubscriptions(eq(o), eq(p))).thenReturn(true);

        assertThrows(BadRequestException.class, () -> pr.deleteProduct("owner", "10"));
    }

    @Test
    public void testUpdateLockedProductFails() {
        Owner owner = this.createOwner("test_owner");
        Product product = this.createProduct("test_product", "test_product", owner);
        ProductDTO pdto = TestUtil.createProductDTO("test_product", "updated_name");
        product.setLocked(true);
        this.productCurator.merge(product);

        assertNotNull(this.ownerProductCurator.getProductById(owner, pdto.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerProductResource.updateProduct(owner.getKey(), pdto.getId(), pdto)
        );
        Product entity = this.ownerProductCurator.getProductById(owner, pdto.getId());
        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);

        assertNotNull(entity);
        assertNotEquals(expected, pdto);
    }

    @Test
    public void testDeleteLockedProductFails() {
        Owner owner = this.createOwner("test_owner");
        Product product = this.createProduct("test_product", "test_product", owner);
        product.setLocked(true);
        this.productCurator.merge(product);

        assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerProductResource.deleteProduct(owner.getKey(), product.getId())
        );
        assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));
    }

    @Test
    public void getProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        Product entity = this.createProduct("test_product", "test_product", owner);

        securityInterceptor.enable();
        ProductDTO result = this.ownerProductResource.getProduct(owner.getKey(), entity.getId());
        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);

        assertNotNull(result);
        assertEquals(expected, result);
    }

    @Test
    public void getProductCertificate() {
        Owner owner = this.createOwner("Example-Corporation");

        Product entity = this.createProduct("123", "AwesomeOS Core", owner);
        // ensure we check SecurityHole
        securityInterceptor.enable();

        ProductCertificate cert = new ProductCertificate();
        cert.setCert("some text");
        cert.setKey("some key");
        cert.setProduct(entity);
        productCertificateCurator.create(cert);

        ProductCertificateDTO cert1 = ownerProductResource.getProductCertificate(owner.getKey(),
            entity.getId());
        ProductCertificateDTO expected = this.modelTranslator.translate(cert, ProductCertificateDTO.class);
        assertEquals(cert1, expected);
    }

    @Test
    public void requiresNumericIdForProductCertificates() {
        Owner owner = this.createOwner("Example-Corporation");

        Product entity = this.createProduct("MCT123", "AwesomeOS", owner);
        securityInterceptor.enable();

        assertThrows(BadRequestException.class, () ->
            ownerProductResource.getProductCertificate(owner.getKey(), entity.getId())
        );
    }
}
