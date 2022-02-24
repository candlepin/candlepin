/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class PromotedContentTest {

    public static final String ENV_ID = "env_id_1";
    public static final String CONTENT_ID = "content_id_1";

    @Test
    void nullProductContent() {
        PromotedContent promotedContent = new PromotedContent(emptyPrefix());

        String environmentId = promotedContent.environmentIdOf(null);

        assertNull(environmentId);
    }

    @Test
    void environmentNotFound() {
        Content content = this.mockContent();
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(emptyPrefix());

        String environmentId = promotedContent.environmentIdOf(pc);

        assertNull(environmentId);
    }

    @Test
    void environmentFound() {
        Content content = this.mockContent();
        Environment environment = createEnvironment(content);
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(emptyPrefix())
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

        PromotedContent promotedContent = new PromotedContent(emptyPrefix())
            .with(environment1)
            .with(environment2);

        assertEquals(environment1.getId(), promotedContent.environmentIdOf(pc1));
        assertEquals(environment1.getId(), promotedContent.environmentIdOf(pc2));
    }

    @Test
    void nullIsNotPromoted() {
        PromotedContent promotedContent = new PromotedContent(emptyPrefix());

        boolean isPromoted = promotedContent.contains(null);

        assertFalse(isPromoted);
    }

    @Test
    void unknownContentIsNotPromoted() {
        Content content = this.mockContent();
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(emptyPrefix());

        boolean isPromoted = promotedContent.contains(pc);

        assertFalse(isPromoted);
    }

    @Test
    void knownPromotedContent() {
        Content content = this.mockContent();
        Environment environment = createEnvironment(content);
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(emptyPrefix())
            .with(environment);

        boolean isPromoted = promotedContent.contains(pc);

        assertTrue(isPromoted);
    }

    @Test
    void nullIsNotEnabled() {
        PromotedContent promotedContent = new PromotedContent(emptyPrefix());

        boolean isEnabled = promotedContent.isEnabled(null);

        assertFalse(isEnabled);
    }

    @Test
    void unknownContentIsNotEnabled() {
        Content content = this.mockContent();
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(emptyPrefix());

        boolean isEnabled = promotedContent.isEnabled(pc);

        assertFalse(isEnabled);
    }

    @Test
    void knownEnabledContent() {
        Content content = this.mockContent();
        Environment environment = createEnvironment(content);
        Product product = this.mockProduct(content);
        ProductContent pc = new ProductContent(product, content, true);
        PromotedContent promotedContent = new PromotedContent(emptyPrefix())
            .with(environment);

        boolean isEnabled = promotedContent.isEnabled(pc);

        assertTrue(isEnabled);
    }

    @ParameterizedTest
    @MethodSource("invalidPrefix")
    public void invalidPrefixIsOmitted(String prefix) {
        PromotedContent promotedContent = createPromotedContent(prefix);
        ProductContent pc = createContent("some/path");

        assertEquals("/some/path", promotedContent.getPath(pc));
    }

    public static Stream<Arguments> invalidPrefix() {
        return Stream.of(
            Arguments.of((String) null),
            Arguments.of("")
        );
    }

    @ParameterizedTest
    @MethodSource("endingSlash")
    public void shouldHandleRepeatedSlashesInPrefix(String prefix) {
        PromotedContent promotedContent = createPromotedContent(prefix);
        ProductContent pc = createContent("/some/path");

        assertEquals("/this/is/some/path", promotedContent.getPath(pc));
    }

    public static Stream<Arguments> endingSlash() {
        return Stream.of(
            Arguments.of("/this/is"),
            Arguments.of("/this/is/"),
            Arguments.of("/this/is///")
        );
    }

    @Test
    public void missingStartingSlashInContentUrl() {
        PromotedContent promotedContent = createPromotedContent("/this/is///");
        ProductContent pc = createContent("some/path");

        assertEquals("/this/is/some/path", promotedContent.getPath(pc));
    }

    @Test
    public void repeatedStartingSlashInContentUrl() {
        PromotedContent promotedContent = createPromotedContent("/this/is///");
        ProductContent pc = createContent("/////////some/path");

        assertEquals("/this/is/some/path", promotedContent.getPath(pc));
    }

    @ParameterizedTest
    @MethodSource("endingSlash")
    public void shouldHandleStartingSlash(String prefix) {
        PromotedContent promotedContent = createPromotedContent(prefix);
        ProductContent pc = createContent("some/path");

        assertEquals("/this/is/some/path", promotedContent.getPath(pc));
    }

    @ParameterizedTest
    @MethodSource("urlsWithProtocol")
    public void urlsWithProtocolShouldNotBePrefixed(String contentUrl) {
        PromotedContent promotedContent = createPromotedContent("/this/is");
        ProductContent pc = createContent(contentUrl);

        assertEquals(contentUrl, promotedContent.getPath(pc));
    }

    public static Stream<Arguments> urlsWithProtocol() {
        return Stream.of(
            Arguments.of("http://some/path"),
            Arguments.of("https://some/path"),
            Arguments.of("ftp://some/path"),
            Arguments.of("file://some/path")
        );
    }

    private Environment createEnvironment(Content content) {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Environment environment = this.mockEnvironment(owner, consumer, content);
        environment.getEnvironmentContent().add(new EnvironmentContent(environment, content, true));
        return environment;
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

        Consumer consumer = new Consumer("Test Consumer", "bob", owner, type);
        consumer.setUuid("test-consumer-uuid");
        consumer.setId("test-consumer-id");

        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);
        consumer.setCapabilities(Util.asSet(new ConsumerCapability(consumer, "cert_v3")));

        return consumer;
    }

    private Content mockContent() {
        return mockContent("test_content");
    }

    private Content mockContent(String name) {
        Content content = new Content();
        content.setUuid("test_content-uuid");
        content.setId("1234");
        content.setName(name);
        content.setLabel("test_content-label");
        content.setType("yum");
        content.setVendor("vendor");
        content.setContentUrl("/content/dist/rhel/$releasever/$basearch/os");
        content.setGpgUrl("gpgUrl");
        content.setArches("x86_64");
        content.setMetadataExpiration(3200L);
        content.setRequiredTags("TAG1,TAG2");

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
        Environment environment = new Environment("test_environment", "test_environment", owner);
        environment.setEnvironmentContent(Util.asSet(new EnvironmentContent(environment, content, true)));

        consumer.addEnvironment(environment);

        return environment;
    }

    private ProductContent createContent(String contentUrl) {
        ProductContent productContent = new ProductContent();
        Content content = new Content()
            .setId(CONTENT_ID)
            .setContentUrl(contentUrl);
        productContent.setContent(content);
        return productContent;
    }

    private PromotedContent createPromotedContent(String prefix) {
        Environment environment = new Environment(ENV_ID, "env1", new Owner());
        environment.getEnvironmentContent()
            .add(new EnvironmentContent(environment, new Content(CONTENT_ID), true));

        return new PromotedContent(prefix(prefix))
            .with(environment);
    }

    private ContentPrefix emptyPrefix() {
        return envId -> "";
    }

    private ContentPrefix prefix(String prefix) {
        return envId -> prefix;
    }

}
