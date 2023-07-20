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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.dto.api.server.v1.AttributeDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.ProductCertificateDTO;
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;



public class OwnerProductResourceTest extends DatabaseTestFixture {

    private OwnerProductResource ownerProductResource;
    private JobManager jobManager;

    @BeforeEach
    public void setUp() {
        ownerProductResource = this.injector.getInstance(OwnerProductResource.class);
        this.jobManager = mock(JobManager.class);
    }

    private ProductDTO buildTestProductDTO() {
        return TestUtil.createProductDTO("test_product")
            .addAttributesItem(this.createAttribute(Product.Attributes.VERSION, "1.0"))
            .addAttributesItem(this.createAttribute(Product.Attributes.VARIANT, "server"))
            .addAttributesItem(this.createAttribute(Product.Attributes.TYPE, "SVC"))
            .addAttributesItem(this.createAttribute(Product.Attributes.ARCHITECTURE, "ALL"));
    }

    private AttributeDTO createAttribute(String name, String value) {
        return new AttributeDTO()
            .name(name)
            .value(value);
    }

    private void addContent(ProductDTO product, ContentDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new IllegalArgumentException("dto references incomplete content");
        }

        if (product.getProductContent() == null) {
            product.setProductContent(new HashSet<>());
        }

        ProductContentDTO content = new ProductContentDTO();
        content.setContent(dto);
        content.setEnabled(true);

        product.getProductContent().add(content);
    }

    @Test
    public void testCreateProductResource() {
        Owner owner = this.createOwner("Example-Corporation");
        ProductDTO pdto = this.buildTestProductDTO();

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), pdto.getId()));

        ProductDTO result = this.ownerProductResource
            .createProductByOwner(owner.getKey(), pdto);
        Product entity = this.ownerProductCurator.getProductById(owner, pdto.getId());
        ProductDTO expected = this.modelTranslator.translate(entity,
            ProductDTO.class);

        assertNotNull(result);
        assertNotNull(entity);
        assertEquals(expected, result);
    }

    @Test
    public void testCreateProductWithAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "value-3");

        Owner owner = this.createOwner("Test org");
        ProductDTO pdto = new ProductDTO()
            .id("test_prod-1")
            .name("test product 1");

        attributes.forEach((k, v) -> pdto.addAttributesItem(this.createAttribute(k, v)));

        ProductDTO output = this.ownerProductResource.createProductByOwner(owner.getKey(), pdto);

        assertNotNull(output);
        assertEquals(pdto.getId(), output.getId());
        assertEquals(pdto.getName(), output.getName());
        assertNotNull(output.getAttributes());
        assertEquals(attributes.size(), output.getAttributes().size());

        for (AttributeDTO attrib : output.getAttributes()) {
            assertTrue(attributes.containsKey(attrib.getName()));
            assertEquals(attributes.get(attrib.getName()), attrib.getValue());
        }
    }

    @Test
    public void testCreateProductFiltersUnusableAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "value-3");
        attributes.put("", "dropped");
        attributes.put("dropped", null);

        Map<String, String> expected = new HashMap<>();
        expected.put("attrib-1", "value-1");
        expected.put("attrib-2", "value-2");
        expected.put("attrib-3", "value-3");

        Owner owner = this.createOwner("Test org");
        ProductDTO pdto = new ProductDTO()
            .id("test_prod-1")
            .name("test product 1");

        attributes.forEach((k, v) -> pdto.addAttributesItem(this.createAttribute(k, v)));

        // Add some dud attributes to ensure filtering is occurring for other types of malformed
        // attribute data
        pdto.addAttributesItem(null);
        pdto.addAttributesItem(this.createAttribute(null, "dropped"));

        ProductDTO output = this.ownerProductResource.createProductByOwner(owner.getKey(), pdto);

        assertNotNull(output);
        assertEquals(pdto.getId(), output.getId());
        assertEquals(pdto.getName(), output.getName());
        assertNotNull(output.getAttributes());
        assertEquals(expected.size(), output.getAttributes().size());

        for (AttributeDTO attrib : output.getAttributes()) {
            assertTrue(expected.containsKey(attrib.getName()));
            assertEquals(expected.get(attrib.getName()), attrib.getValue());
        }
    }

    @Test
    public void testCreateProductWithContent() {
        Owner owner = this.createOwner("Example-Corporation");
        Content content = this.createContent("content-1", "content-1", owner);
        ProductDTO product = this.buildTestProductDTO();
        ContentDTO contentDTO = this.modelTranslator.translate(content, ContentDTO.class);
        addContent(product, contentDTO);

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), product.getId()));

        ProductDTO result = this.ownerProductResource
            .createProductByOwner(owner.getKey(), product);
        Product entity = this.ownerProductCurator.getProductById(owner, product.getId());
        ProductDTO expected = this.modelTranslator.translate(entity,
            ProductDTO.class);

        assertNotNull(result);
        assertNotNull(entity);
        assertEquals(expected, result);

        assertNotNull(result.getProductContent());
        assertEquals(1, result.getProductContent().size());
        assertEquals(contentDTO, result.getProductContent().iterator().next().getContent());
    }

    @Test
    public void testUpdateProductWithAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "value-3");

        Owner owner = this.createOwner("Test org");
        Product existing = this.createProduct("test_prod-1", "test product 1", owner);

        assertNotNull(existing);
        assertTrue(existing.getAttributes().isEmpty());

        ProductDTO pdto = new ProductDTO()
            .id(existing.getId());

        attributes.forEach((k, v) -> pdto.addAttributesItem(this.createAttribute(k, v)));

        ProductDTO output = this.ownerProductResource.updateProductByOwner(owner.getKey(),
            existing.getId(), pdto);

        assertNotNull(output);
        assertEquals(existing.getId(), output.getId());
        assertEquals(existing.getName(), output.getName());
        assertNotNull(output.getAttributes());
        assertEquals(attributes.size(), output.getAttributes().size());

        for (AttributeDTO attrib : output.getAttributes()) {
            assertTrue(attributes.containsKey(attrib.getName()));
            assertEquals(attributes.get(attrib.getName()), attrib.getValue());
        }
    }

    @Test
    public void testUpdateProductFiltersUnusableAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "value-3");
        attributes.put("", "dropped");
        attributes.put("dropped", null);

        Map<String, String> expected = new HashMap<>();
        expected.put("attrib-1", "value-1");
        expected.put("attrib-2", "value-2");
        expected.put("attrib-3", "value-3");

        Owner owner = this.createOwner("Test org");
        Product existing = this.createProduct("test_prod-1", "test product 1", owner);

        assertNotNull(existing);
        assertTrue(existing.getAttributes().isEmpty());

        ProductDTO pdto = new ProductDTO()
            .id(existing.getId());

        attributes.forEach((k, v) -> pdto.addAttributesItem(this.createAttribute(k, v)));

        // Add some dud attributes to ensure filtering is occurring for other types of malformed
        // attribute data
        pdto.addAttributesItem(null);
        pdto.addAttributesItem(this.createAttribute(null, "dropped"));

        ProductDTO output = this.ownerProductResource.updateProductByOwner(owner.getKey(),
            existing.getId(), pdto);

        assertNotNull(output);
        assertEquals(existing.getId(), output.getId());
        assertEquals(existing.getName(), output.getName());
        assertNotNull(output.getAttributes());
        assertEquals(expected.size(), output.getAttributes().size());

        for (AttributeDTO attrib : output.getAttributes()) {
            assertTrue(expected.containsKey(attrib.getName()));
            assertEquals(expected.get(attrib.getName()), attrib.getValue());
        }
    }

    @Test
    public void testUpdateProductWithoutId() {
        Owner owner = this.createOwner("Update-Product-Owner");
        ProductDTO pdto = this.buildTestProductDTO();

        ProductDTO product = this.ownerProductResource
            .createProductByOwner(owner.getKey(), pdto);
        ProductDTO update = TestUtil.createProductDTO(product.getId());
        update.setName(product.getName());
        update.getAttributes().add(createAttribute("attri", "bute"));
        ProductDTO result = this.ownerProductResource
            .updateProductByOwner(owner.getKey(), product.getId(), update);
        assertEquals("bute", result.getAttributes().get(0).getValue());
    }

    @Test
    public void testUpdateProductIdMismatch() {
        Owner owner = this.createOwner("Update-Product-Owner");
        ProductDTO pdto = this.buildTestProductDTO();
        ProductDTO product = this.ownerProductResource
            .createProductByOwner(owner.getKey(), pdto);
        ProductDTO update = this.buildTestProductDTO();
        update.setId("TaylorSwift");

        assertThrows(BadRequestException.class,
            () -> this.ownerProductResource.updateProductByOwner(owner.getKey(), product.getId(), update));
    }

    @Test
    public void testDeleteProductWithSubscriptions() {
        OwnerCurator oc = mock(OwnerCurator.class);
        OwnerProductCurator opc = mock(OwnerProductCurator.class);
        ProductCurator pc = mock(ProductCurator.class);
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        OwnerProductResource ownerres = new OwnerProductResource(oc, i18n, null,
            opc, null, null, null, null,
            null, null, pc);

        Owner o = mock(Owner.class);
        Product p = mock(Product.class);

        when(oc.getByKey(eq("owner"))).thenReturn(o);
        when(opc.getProductById(eq(o), eq("10"))).thenReturn(p);

        Set<Subscription> subs = new HashSet<>();
        Subscription s = mock(Subscription.class);
        subs.add(s);
        when(pc.productHasSubscriptions(eq(o), eq(p))).thenReturn(true);

        assertThrows(BadRequestException.class, () -> ownerres.deleteProductByOwner("owner", "10"));
    }

    @Test
    public void testUpdateLockedProductFails() {
        Owner owner = this.createOwner("test_owner");
        Product product = this.createProduct("test_product", "test_product", owner);
        ProductDTO pdto = TestUtil.createProductDTO("test_product", "updated_name");
        product.setLocked(true);
        this.productCurator.merge(product);

        assertNotNull(this.ownerProductCurator.getProductById(owner, pdto.getId()));

        assertThrows(ForbiddenException.class,
            () -> this.ownerProductResource.updateProductByOwner(owner.getKey(), pdto.getId(), pdto));
        Product entity = this.ownerProductCurator.getProductById(owner, pdto.getId());
        ProductDTO expected = this.modelTranslator.translate(entity,
            ProductDTO.class);

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

        assertThrows(ForbiddenException.class,
            () -> this.ownerProductResource.deleteProductByOwner(owner.getKey(), product.getId()));
        assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));
    }

    @Test
    public void getProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        Product entity = this.createProduct("test_product", "test_product", owner);

        securityInterceptor.enable();
        ProductDTO result = this.ownerProductResource.getProductByOwner(owner.getKey(),
            entity.getId());
        ProductDTO expected = this.modelTranslator.translate(entity,
            ProductDTO.class);

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

        ProductCertificateDTO cert1 = ownerProductResource.getProductCertificateByOwner(owner.getKey(),
            entity.getId());
        ProductCertificateDTO expected = this.modelTranslator.translate(cert, ProductCertificateDTO.class);
        assertEquals(cert1, expected);
    }

    @Test
    public void requiresNumericIdForProductCertificates() {
        Owner owner = this.createOwner("Example-Corporation");

        Product entity = this.createProduct("MCT123", "AwesomeOS", owner);
        securityInterceptor.enable();

        assertThrows(BadRequestException.class,
            () -> ownerProductResource.getProductCertificateByOwner(owner.getKey(), entity.getId()));
    }
}
