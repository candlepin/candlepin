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
package org.candlepin.controller.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.util.X509V3ExtensionUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;


class PromotedContentTest {

    public static final String ENV_ID = "env_id_1";
    public static final String CONTENT_ID = "content_id_1";

    @Test
    void nullProductContent() {
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder());

        String environmentId = promotedContent.environmentIdOf(null);

        assertNull(environmentId);
    }

    @Test
    void environmentNotFound() {
        Content content = this.mockContent();
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder());

        String environmentId = promotedContent.environmentIdOf(pc);

        assertNull(environmentId);
    }

    @Test
    void environmentFound() {
        Content content = this.mockContent();
        Environment environment = createEnvironment(content);
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder())
            .with(environment);

        String environmentId = promotedContent.environmentIdOf(pc);

        assertEquals(environment.getId(), environmentId);
    }

    @Test
    void contentShouldBeDeDuplicated() {
        Content content1 = this.mockContent("content_1");
        Content content2 = this.mockContent("content_2");
        Product product = this.mockProduct(content1, content2);
        ProductContent pc1 = new ProductContent(product, content1, true);
        ProductContent pc2 = new ProductContent(product, content2, true);
        Environment environment1 = createEnvironment(content1);
        Environment environment2 = createEnvironment(content2);

        PromotedContent promotedContent = new PromotedContent(contentPathBuilder())
            .with(environment1)
            .with(environment2);

        assertEquals(environment1.getId(), promotedContent.environmentIdOf(pc1));
        assertEquals(environment1.getId(), promotedContent.environmentIdOf(pc2));
    }

    @Test
    void nullIsNotPromoted() {
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder());

        boolean isPromoted = promotedContent.contains(null);

        assertFalse(isPromoted);
    }

    @Test
    void unknownContentIsNotPromoted() {
        Content content = this.mockContent();
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder());

        boolean isPromoted = promotedContent.contains(pc);

        assertFalse(isPromoted);
    }

    @Test
    void knownPromotedContent() {
        Content content = this.mockContent();
        Environment environment = createEnvironment(content);
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder())
            .with(environment);

        boolean isPromoted = promotedContent.contains(pc);

        assertTrue(isPromoted);
    }

    @Test
    void nullIsNotEnabled() {
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder());

        boolean isEnabled = promotedContent.isEnabled(null);

        assertFalse(isEnabled);
    }

    @Test
    void unknownContentIsNotEnabled() {
        Content content = this.mockContent();
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder());

        boolean isEnabled = promotedContent.isEnabled(pc);

        assertFalse(isEnabled);
    }

    @Test
    void knownEnabledContent() {
        Content content = this.mockContent();
        Environment environment = createEnvironment(content);
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder())
            .with(environment);

        boolean isEnabled = promotedContent.isEnabled(pc);

        assertTrue(isEnabled);
    }

    @Test
    public void shouldBuildContentPath() {
        PromotedContent promotedContent = createPromotedContent();
        ProductContent pc = createContent("some/path");

        assertEquals("/some/path", promotedContent.getPath(pc));
    }

    private Environment createEnvironment(Content content) {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);

        return this.mockEnvironment(owner, consumer, content);
    }


    private Owner mockOwner() {
        Owner owner = new Owner();
        owner.setId("test_owner");
        owner.setKey("test_owner");

        return owner;
    }

    private Consumer mockConsumer(Owner owner) {
        ConsumerType type = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        type.setId("test-id");

        Consumer consumer = new Consumer()
            .setUuid("test-consumer-uuid")
            .setId("test-consumer-id")
            .setName("Test Consumer")
            .setUsername("bob")
            .setOwner(owner)
            .setType(type)
            .setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, X509V3ExtensionUtil.CERT_VERSION)
            .setCapabilities(Set.of(new ConsumerCapability("cert_v3")));

        return consumer;
    }

    private Content mockContent() {
        return mockContent("test_content");
    }

    private Content mockContent(String name) {
        Content content = new Content("1234")
            .setUuid("test_content-uuid")
            .setName(name)
            .setLabel("test_content-label")
            .setType("yum")
            .setVendor("vendor")
            .setContentUrl("/content/dist/rhel/$releasever/$basearch/os")
            .setGpgUrl("gpgUrl")
            .setArches("x86_64")
            .setMetadataExpiration(3200L)
            .setRequiredTags("TAG1,TAG2");

        return content;
    }

    private Product mockProduct(Content... contents) {
        Product product = new Product("test_product_id", "test_product", null);
        product.setAttribute(Product.Attributes.VERSION, "version");
        product.setAttribute(Product.Attributes.VARIANT, "variant");
        product.setAttribute(Product.Attributes.TYPE, "SVC");
        product.setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        for (Content content : contents) {
            product.addContent(content, false);
        }

        return product;
    }

    private Environment mockEnvironment(Owner owner, Consumer consumer, Content content) {
        int rnd = (int) (Math.random() * 10000);

        Environment environment = new Environment()
            .setId("test_environment-" + rnd)
            .setName("test environment " + rnd)
            .setOwner(owner);

        EnvironmentContent ec = new EnvironmentContent()
            .setEnvironment(environment)
            .setContent(content)
            .setEnabled(true);

        environment.addEnvironmentContent(ec);

        consumer.addEnvironment(environment);

        return environment;
    }

    private ProductContent createContent(String contentUrl) {
        Content content = new Content(CONTENT_ID)
            .setContentUrl(contentUrl);

        return new ProductContent(content, false);
    }

    private PromotedContent createPromotedContent() {
        Owner owner = new Owner()
            .setId("owner-1");

        Environment environment = new Environment()
            .setId(ENV_ID)
            .setName("env1")
            .setOwner(owner);

        EnvironmentContent envcontent = new EnvironmentContent()
            .setEnvironment(environment)
            .setContent(new Content(CONTENT_ID))
            .setEnabled(true);

        environment.addEnvironmentContent(envcontent);

        return new PromotedContent(contentPathBuilder())
            .with(environment);
    }

    private ContentPathBuilder contentPathBuilder() {
        return ContentPathBuilder.from(new Owner(), List.of());
    }

}
