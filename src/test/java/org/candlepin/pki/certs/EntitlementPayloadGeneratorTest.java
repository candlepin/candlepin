/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki.certs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.dto.EntitlementBody;
import org.candlepin.model.dto.Service;
import org.candlepin.model.dto.TinySubscription;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.InflaterOutputStream;


public class EntitlementPayloadGeneratorTest {

    private EntitlementPayloadGenerator generator;

    @BeforeEach
    public void setUp() {
        ObjectMapper mapper = ObjectMapperFactory.getX509V3ExtensionUtilObjectMapper();
        this.generator = new EntitlementPayloadGenerator(mapper);
    }

    @Test
    public void testPrepareV3EntitlementData() {
        Content content = createContent();
        Product product = createProduct().addContent(content, true);
        product.setAttribute(Product.Attributes.WARNING_PERIOD, "20");
        product.setAttribute(Product.Attributes.SOCKETS, "4");
        product.setAttribute(Product.Attributes.RAM, "8");
        product.setAttribute(Product.Attributes.CORES, "4");
        product.setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "true");
        product.setAttribute(Product.Attributes.STACKING_ID, "45678");
        product.setAttribute(Product.Attributes.SUPPORT_LEVEL, "slevel");
        product.setAttribute(Product.Attributes.SUPPORT_TYPE, "stype");
        Pool pool = createPool(product);
        List<org.candlepin.model.dto.Product> products = List.of(createProductDto());

        byte[] payload = this.generator.generate(products, "consumerUuid", pool, 10);

        EntitlementBody result = Util.fromJson(processPayload(payload), EntitlementBody.class);
        assertThat(result.getSubscription())
            .returns(20, TinySubscription::getWarning)
            .returns(4, TinySubscription::getSockets)
            .returns(8, TinySubscription::getRam)
            .returns(4, TinySubscription::getCores)
            .returns(true, TinySubscription::getManagement)
            .returns("45678", TinySubscription::getStackingId);
        assertThat(result.getSubscription().getService())
            .returns("slevel", Service::getLevel)
            .returns("stype", Service::getType);
    }

    @Test
    public void testPrepareV3EntitlementDataForDefaults() {
        Content content = createContent();
        Product product = createProduct().addContent(content, true);
        product.setAttribute(Product.Attributes.WARNING_PERIOD, "0");
        product.setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "false");
        Pool pool = createPool(product);
        pool.setAttribute(Product.Attributes.VIRT_ONLY, "false");
        List<org.candlepin.model.dto.Product> products = List.of(createProductDto());

        byte[] payload = this.generator.generate(products, "consumerUuid", pool, 10);

        EntitlementBody result = Util.fromJson(processPayload(payload), EntitlementBody.class);
        assertThat(result.getSubscription())
            .returns(null, TinySubscription::getManagement)
            .returns(null, TinySubscription::getWarning)
            .returns(null, TinySubscription::getVirtOnly);
        assertThat(result.getProducts())
            .flatMap(org.candlepin.model.dto.Product::getContent)
            .map(org.candlepin.model.dto.Content::getEnabled)
            .containsOnly(true);
    }

    @Test
    public void testPrepareV3EntitlementDataForBooleans() {
        Content content = createContent();
        Product product = createProduct().addContent(content, true);
        product.setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "1");
        Pool pool = createPool(product);
        pool.setAttribute(Product.Attributes.VIRT_ONLY, "1");
        List<org.candlepin.model.dto.Product> products = List.of(createProductDto());

        byte[] payload = this.generator.generate(products, "consumerUuid", pool, 10);

        EntitlementBody result = Util.fromJson(processPayload(payload), EntitlementBody.class);
        assertThat(result.getSubscription())
            .returns(true, TinySubscription::getManagement)
            .returns(true, TinySubscription::getVirtOnly);
    }

    @Test
    public void subscriptionWithSysPurposeAttributes() {
        Content content = createContent();
        Product product = createProduct().addContent(content, true);
        product.setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "1");
        List<org.candlepin.model.dto.Product> products = List.of(createProductDto());
        Owner owner = new Owner()
            .setKey("Test Corporation")
            .setDisplayName("Test Corporation");
        Product mktProd = new Product("mkt", "MKT SKU");
        mktProd.setAttribute(Product.Attributes.USAGE, "my_usage");
        mktProd.setAttribute(Product.Attributes.SUPPORT_LEVEL, "my_support_level");
        mktProd.setAttribute(Product.Attributes.SUPPORT_TYPE, "my_support_type");
        mktProd.setAttribute(Product.Attributes.ROLES, " my_role1, my_role2 , my_role3 ");
        mktProd.setAttribute(Product.Attributes.ADDONS, " my_addon1, my_addon2 , my_addon3 ");
        Pool pool = TestUtil.createPool(owner, mktProd);
        pool.setAttribute(Product.Attributes.VIRT_ONLY, "1");

        byte[] payload = this.generator.generate(products, "consumerUuid", pool, 10);

        EntitlementBody result = Util.fromJson(processPayload(payload), EntitlementBody.class);
        TinySubscription subscription = result.getSubscription();
        assertEquals("my_usage", subscription.getUsage());

        Service service = subscription.getService();
        assertEquals("my_support_level", service.getLevel());
        assertEquals("my_support_type", service.getType());

        assertThat(subscription.getRoles())
            .containsExactlyInAnyOrder(
                "my_role1",
                "my_role2",
                "my_role3"
            );
        assertThat(subscription.getAddons())
            .containsExactlyInAnyOrder(
                "my_addon1",
                "my_addon2",
                "my_addon3"
            );
    }

    private org.candlepin.model.dto.Product createProductDto() {
        org.candlepin.model.dto.Product product = new org.candlepin.model.dto.Product();
        product.setId("" + TestUtil.randomInt());
        product.setName(TestUtil.randomString());
        product.setArchitectures(List.of("ALL"));
        product.setContent(List.of(createContentDto(), createContentDto()));
        product.setBrandName("brand_1");
        product.setBrandType("brand_type_1");
        return product;
    }

    private org.candlepin.model.dto.Content createContentDto() {
        org.candlepin.model.dto.Content content = new org.candlepin.model.dto.Content();
        content.setId("" + TestUtil.randomInt());
        content.setName(TestUtil.randomString());
        content.setLabel("label");
        content.setVendor("vendor");
        content.setArches(Arrays.asList("x86_64".split(",")));
        content.setPath("/path/to/somewhere");
        content.setGpgUrl("/path/to/gpg");
        content.setType("yum");
        content.setMetadataExpiration(3200L);
        content.setRequiredTags(List.of("tag_1"));
        content.setEnabled(true);
        return content;
    }

    private Pool createPool(Product product) {
        Pool pool = new Pool();
        pool.setId(TestUtil.randomString());
        pool.setQuantity(10L);
        pool.setProduct(product);
        pool.setStartDate(new Date());
        pool.setEndDate(new Date());
        return pool;
    }

    private Product createProduct() {
        String productId = "12345" + TestUtil.randomInt();
        Product product = TestUtil.createProduct(productId, "a product");
        product.setAttribute(Product.Attributes.VERSION, "version");
        product.setAttribute(Product.Attributes.VARIANT, "variant");
        product.setAttribute(Product.Attributes.TYPE, "SVC");
        product.setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        return product;
    }

    private Content createContent() {
        String id = "" + TestUtil.randomInt(10_000);
        Content content = TestUtil.createContent(id, TestUtil.randomString())
            .setUuid(id + "_uuid")
            .setLabel("label")
            .setType("yum")
            .setVendor("vendor")
            .setArches("x86_64")
            .setRequiredTags("tag1")
            .setMetadataExpiration(3200L);

        return content;
    }

    private String processPayload(byte[] payload) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             InflaterOutputStream ios = new InflaterOutputStream(baos)) {
            ios.write(payload);
            ios.finish();
            return baos.toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
