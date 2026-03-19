/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessPayload;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.dto.EntitlementBody;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.X509V3ExtensionUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;



public class ContentAccessPayloadBuilderTest extends DatabaseTestFixture {

    // The crypto manager we'll use for these tests
    private static final CryptoManager CRYPTO_MANAGER = CryptoUtil.getCryptoManager();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    private EntitlementPayloadGenerator buildEntitlementPayloadGenerator() {
        return this.injector.getInstance(EntitlementPayloadGenerator.class);
    }

    private X509V3ExtensionUtil buildV3ExtensionUtil() {
        return this.injector.getInstance(X509V3ExtensionUtil.class);
    }

    private ContentAccessPayloadBuilder buildContentAccessPayloadBuilder() {
        return new ContentAccessPayloadBuilder(
            CRYPTO_MANAGER,
            this.buildEntitlementPayloadGenerator(),
            this.buildV3ExtensionUtil(),
            this.contentCurator,
            this.caPayloadCurator);
    }

    private Content buildContent(String architecture) {
        String suffix = TestUtil.randomString(8);

        return this.createContent(new Content("test_content-" + suffix)
            .setName("test_content-" + suffix)
            .setType("test-type")
            .setLabel("test-label-" + suffix)
            .setVendor("test-vendor")
            .setContentUrl("/test/content/" + suffix)
            .setGpgUrl("https://gpg.test.url.com/" + suffix)
            .setArches(architecture));
    }

    private Environment buildEnvironment(Owner owner, String name) {
        return this.createEnvironment(owner, name)
            .setContentPrefix("/" + name);
    }

    private Product generateProductForContents(boolean enabled, Content... contents) {
        Product engProduct = this.createProduct();
        for (Content content : contents) {
            engProduct.addContent(content, enabled);
        }

        return this.createProduct()
            .setProvidedProducts(List.of(engProduct));
    }

    private Pool generateActivePoolForContents(Owner owner, boolean enabled, Content... contents) {
        Product skuProduct = this.generateProductForContents(enabled, contents);
        return this.createPool(owner, skuProduct);
    }

    private Pool generateFuturePoolForContents(Owner owner, boolean enabled, Content... contents) {
        Product skuProduct = this.generateProductForContents(enabled, contents);
        Date startDate = Date.from(Instant.now().plusSeconds(86400));

        return this.createPool(owner, skuProduct)
            .setStartDate(startDate);
    }

    private Pool generateExpiredPoolForContents(Owner owner, boolean enabled, Content... contents) {
        Product skuProduct = this.generateProductForContents(enabled, contents);
        Date endDate = Date.from(Instant.now().minusSeconds(86400));

        return this.createPool(owner, skuProduct)
            .setEndDate(endDate);
    }

    private void promoteContentToEnvironments(Content content, boolean enabled, Environment... environments) {
        for (Environment environment : environments) {
            EnvironmentContent environmentContent = new EnvironmentContent()
                .setEnvironment(environment)
                .setContent(content)
                .setEnabled(enabled);

            this.environmentContentCurator.create(environmentContent);
            environment.addEnvironmentContent(environmentContent);
        }
    }

    private List<ProductContent> generateContent(Set<Owner> owners, Set<String> architectures) {
        Function<String, Product> generator = arch -> {
            Content content = this.buildContent(arch);

            Product product = this.createProduct()
                .addContent(content, true);

            return product;
        };

        Set<String> archSet = new HashSet<>(architectures);
        archSet.add(null);

        List<Product> engProducts = archSet.stream()
            .map(generator)
            .toList();

        Product skuProduct = this.createProduct()
            .setProvidedProducts(engProducts);

        owners.forEach(owner -> this.createPool(owner, skuProduct));

        this.productCurator.flush();

        return engProducts.stream()
            .flatMap(product -> product.getProductContent().stream())
            .toList();
    }

    private byte[] parsePayloadSection(String payload, String header, String footer) {
        assertTrue(payload.contains(header));
        assertTrue(payload.contains(footer));

        int offset = payload.indexOf(header) + header.length();
        int endIndex = payload.indexOf(footer);

        assertTrue(endIndex > offset);

        String b64data = payload.substring(offset, endIndex);

        return Base64.getMimeDecoder()
            .decode(b64data);
    }

    private EntitlementBody parsePayloadContent(ContentAccessPayload payload) throws IOException {
        String contentHeader = "-----BEGIN ENTITLEMENT DATA-----";
        String contentFooter = "-----END ENTITLEMENT DATA-----";

        byte[] content = this.parsePayloadSection(payload.getPayload(), contentHeader, contentFooter);

        try (InputStream istream = new InflaterInputStream(new ByteArrayInputStream(content))) {
            return OBJECT_MAPPER.readValue(istream, EntitlementBody.class);
        }
    }

    private Map<String, org.candlepin.model.dto.Content> mapPayloadContent(ContentAccessPayload payload)
        throws IOException {

        EntitlementBody body = this.parsePayloadContent(payload);

        return body.getProducts()
            .stream()
            .flatMap(product -> product.getContent().stream())
            .collect(Collectors.toMap(org.candlepin.model.dto.Content::getId, Function.identity()));
    }

    private void validatePayloadSignature(ContentAccessPayload payload, Scheme scheme) throws IOException {
        String contentHeader = "-----BEGIN ENTITLEMENT DATA-----";
        String contentFooter = "-----END ENTITLEMENT DATA-----";

        String signatureHeader = "-----BEGIN SIGNATURE-----";
        String signatureFooter = "-----END SIGNATURE-----";

        byte[] content = this.parsePayloadSection(payload.getPayload(), contentHeader, contentFooter);
        byte[] signature = this.parsePayloadSection(payload.getPayload(), signatureHeader, signatureFooter);

        boolean valid = CRYPTO_MANAGER.getSignatureValidator(scheme)
            .forSignature(signature)
            .validate(content);

        assertTrue(valid);
    }

    private java.util.function.Consumer<org.candlepin.model.dto.Content> hasPath(Owner owner, Content content,
        Environment environment) {

        return entry -> {
            // TODO: FIXME: This isn't accurate, but the ContentPathBuilder's implementation is utter
            // nonsense. Fix the ContentPathBuilder, and then update this logic to use it.
            String expectedPath = new StringBuilder()
                .append(Optional.ofNullable(owner.getContentPrefix()).orElse(""))
                .append(Optional.ofNullable(environment.getContentPrefix()).orElse(""))
                .append(content.getContentUrl())
                .toString();

            assertEquals(expectedPath, entry.getPath());
        };
    }

    private java.util.function.Consumer<org.candlepin.model.dto.Content> hasPath(Owner owner,
        Content content) {

        return entry -> {
            // TODO: FIXME: This isn't accurate, but the ContentPathBuilder's implementation is utter
            // nonsense. Fix the ContentPathBuilder, and then update this logic to use it.
            String expectedPath = new StringBuilder()
                .append(Optional.ofNullable(owner.getContentPrefix()).orElse(""))
                .append(content.getContentUrl())
                .toString();

            assertEquals(expectedPath, entry.getPath());
        };
    }

    @Test
    public void testSetSchemeRejectsNulls() throws Exception {
        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.setCryptoScheme(null));
    }

    @Test
    public void testSetOwnerRejectsNulls() throws Exception {
        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.setOwner(null));
    }

    @Test
    public void testSetConsumerRejectsNulls() throws Exception {
        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.setConsumer(null));
    }

    @Test
    public void testSetEnvironmentsAllowsNulls() throws Exception {
        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        // Nothing should happen when we
        builder.setEnvironments(null);
    }

    @Test
    public void testBuildRequiresScheme() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env1");

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder()
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment));

        assertThrows(IllegalStateException.class, () -> builder.build());
    }

    @Test
    public void testBuildRequiresOwner() throws Exception {
        Scheme scheme = CryptoUtil.SUPPORTED_SCHEMES.values().stream().findAny().get();
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env1");

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment));

        assertThrows(IllegalStateException.class, () -> builder.build());
    }

    @Test
    public void testBuildRequiresConsumer() throws Exception {
        Scheme scheme = CryptoUtil.SUPPORTED_SCHEMES.values().stream().findAny().get();
        Owner owner = this.createOwner();
        Environment environment = this.buildEnvironment(owner, "env1");

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setEnvironments(List.of(environment));

        assertThrows(IllegalStateException.class, () -> builder.build());
    }

    @Test
    public void testBuildDoesNotRequiresEnvironments() throws Exception {
        Scheme scheme = CryptoUtil.SUPPORTED_SCHEMES.values().stream().findAny().get();
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer);

        ContentAccessPayload payload = builder.build();
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayload(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2, content3);

        Date preGenerationDate = new Date();

        ContentAccessPayload payload = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .build();

        assertThat(payload)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(payload.getTimestamp())
            .isNotNull()
            .isAfterOrEqualTo(preGenerationDate)
            .isBeforeOrEqualTo(new Date());

        // Verify that our expected content is present, and has the right paths
        assertThat(this.mapPayloadContent(payload))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadWithEnvironment(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2, content3);

        this.promoteContentToEnvironments(content1, true, environment);
        this.promoteContentToEnvironments(content2, true, environment);

        Date preGenerationDate = new Date();

        ContentAccessPayload payload = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(payload.getTimestamp())
            .isNotNull()
            .isAfterOrEqualTo(preGenerationDate)
            .isBeforeOrEqualTo(new Date());

        // Verify that our expected content is present, and has the right paths
        assertThat(this.mapPayloadContent(payload))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadIsDeterministic(Scheme scheme) throws Exception {
        // This test verifies that we'll get the same payload and key every time we generate with the same
        // org+consumer state

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2, content3);

        this.promoteContentToEnvironments(content1, true, environment);
        this.promoteContentToEnvironments(content2, true, environment);

        ContentAccessPayload payload1 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        this.caPayloadCurator.delete(payload1);
        Thread.sleep(2000);

        ContentAccessPayload payload2 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload2)
            .isNotNull()
            .returns(payload1.getOwnerId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(payload1.getTimestamp(), ContentAccessPayload::getTimestamp)
            .returns(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(payload2.getTimestamp())
            .isNotNull()
            .isAfter(payload1.getTimestamp())
            .isBeforeOrEqualTo(new Date());

        // Verify that our expected content is present, and has the right paths
        // Unfortunately we can't just compare the blobs or the content objects directly because timestamps
        // are involved and the DTOs aren't ready for equality checks
        assertThat(this.mapPayloadContent(payload1))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));

        assertThat(this.mapPayloadContent(payload2))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testPayloadGenerationUsesCachedPayloadIfExists(Scheme scheme) throws Exception {
        // Test verifies that an existing payload will be used if it already exists within the org

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2, content3);
        this.promoteContentToEnvironments(content1, true, environment);
        this.promoteContentToEnvironments(content2, true, environment);

        ContentAccessPayload payload1 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        Thread.sleep(2000);

        ContentAccessPayload payload2 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload2)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .returns(payload1.getTimestamp(), ContentAccessPayload::getTimestamp)
            .returns(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .returns(payload1.getPayload(), ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        // Verify that our expected content is present, and has the right paths
        // Note that since we validated that the payload is equal to the previous, we're validating both here
        assertThat(this.mapPayloadContent(payload2))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testPayloadGenerationExpiresCacheWhenOwnerContentChanges(Scheme scheme) throws Exception {
        // Test verifies that an cached payload will not be used if the org's content has changed

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2, content3);

        ContentAccessPayload payload1 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload1))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));

        // We have to pull this off the payload since the payload itself may be reused and updated in place
        Date initPayloadTimestamp = payload1.getTimestamp();

        Thread.sleep(2000);

        // Even though we're adding no new content, we can trigger a payload reroll by updating the org's
        // last content update
        Date updatedTimestamp = new Date();
        owner.setLastContentUpdate(updatedTimestamp);

        ContentAccessPayload payload2 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .build();

        assertThat(payload2)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(initPayloadTimestamp, ContentAccessPayload::getTimestamp)
            .returns(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .returns(payload1.getPayload(), ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(payload2.getTimestamp())
            .isNotNull()
            .isAfter(initPayloadTimestamp)
            .isAfterOrEqualTo(updatedTimestamp);

        assertThat(this.mapPayloadContent(payload2))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testPayloadGenerationExpiresCacheWhenEnvironmentContentChanges(Scheme scheme)
        throws Exception {
        // Test verifies that an cached payload will not be used if the org's content has changed

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2, content3);
        this.promoteContentToEnvironments(content1, true, environment);
        this.promoteContentToEnvironments(content2, true, environment);

        ContentAccessPayload payload1 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload1))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));

        // We have to pull this off the payload since the payload itself may be reused and updated in place
        Date initPayloadTimestamp = payload1.getTimestamp();

        Thread.sleep(2000);

        // Even though we're adding no new content, we can trigger a payload reroll by updating the env's
        // last content update
        Date updatedTimestamp = new Date();
        environment.setLastContentUpdate(updatedTimestamp);

        ContentAccessPayload payload2 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload2)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(initPayloadTimestamp, ContentAccessPayload::getTimestamp)
            .returns(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .returns(payload1.getPayload(), ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(payload2.getTimestamp())
            .isNotNull()
            .isAfter(initPayloadTimestamp)
            .isAfterOrEqualTo(updatedTimestamp);

        assertThat(this.mapPayloadContent(payload2))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testPayloadGenerationDoesNotReusePayloadsAcrossOrgs(Scheme scheme) throws Exception {
        // Test verifies that an existing payload cache will *not* be used if it's owned by another org

        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner2);
        Environment environment1 = this.createEnvironment(owner1)
            .setContentPrefix("/env");
        Environment environment2 = this.createEnvironment(owner2)
            .setContentPrefix("/env");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner1, true, content1, content2, content3);
        this.generateActivePoolForContents(owner2, true, content1, content2, content3);

        this.promoteContentToEnvironments(content1, true, environment1);
        this.promoteContentToEnvironments(content1, true, environment2);
        this.promoteContentToEnvironments(content2, true, environment1);
        this.promoteContentToEnvironments(content2, true, environment2);

        ContentAccessPayload payload1 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner1)
            .setConsumer(consumer1)
            .setEnvironments(List.of(environment1))
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner1.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        Thread.sleep(2000);

        ContentAccessPayload payload2 = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner2)
            .setConsumer(consumer2)
            .setEnvironments(List.of(environment2))
            .build();

        assertThat(payload2)
            .isNotNull()
            .returns(owner2.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(payload1.getTimestamp(), ContentAccessPayload::getTimestamp)
            .doesNotReturn(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .doesNotReturn(payload1.getPayload(), ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        // Verify that both have the same set of content, just that it's not across orgs.
        // Unfortunately we can't just compare the blobs or the content objects directly because timestamps
        // are involved and the DTOs aren't ready for equality checks
        assertThat(this.mapPayloadContent(payload1))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner1, content1, environment1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner1, content2, environment1))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner1, content3));

        assertThat(this.mapPayloadContent(payload2))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner2, content1, environment2))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner2, content2, environment2))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner2, content3));
    }


    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadIgnoresExpiredPools(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2);
        this.generateExpiredPoolForContents(owner, true, content3);

        ContentAccessPayload payload = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        // Verify that our expected content is present, and has the right paths
        assertThat(this.mapPayloadContent(payload))
            .hasSize(2)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadIgnoresFuturedPools(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2);
        this.generateFuturePoolForContents(owner, true, content3);

        ContentAccessPayload payload = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        // Verify that our expected content is present, and has the right paths
        assertThat(this.mapPayloadContent(payload))
            .hasSize(2)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadIgnoresExpiredPoolsWithPromotedContent(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2);
        this.generateExpiredPoolForContents(owner, true, content3);

        this.promoteContentToEnvironments(content1, true, environment);
        this.promoteContentToEnvironments(content2, true, environment);
        this.promoteContentToEnvironments(content3, true, environment);

        ContentAccessPayload payload = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        // Verify that our expected content is present, and has the right paths
        assertThat(this.mapPayloadContent(payload))
            .hasSize(2)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadIgnoresFuturedPoolsWithPromotedContent(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Environment environment = this.buildEnvironment(owner, "env");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");

        this.generateActivePoolForContents(owner, true, content1, content2);
        this.generateFuturePoolForContents(owner, true, content3);

        this.promoteContentToEnvironments(content1, true, environment);
        this.promoteContentToEnvironments(content2, true, environment);
        this.promoteContentToEnvironments(content3, true, environment);

        ContentAccessPayload payload = this.buildContentAccessPayloadBuilder()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        // Verify that our expected content is present, and has the right paths
        assertThat(this.mapPayloadContent(payload))
            .hasSize(2)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadUsesConsumerArchitecture(Scheme scheme) throws Exception {
        // This test verifies that the consumer ARCHITECTURE fact is a component of the payload key and
        // generation

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");
        Environment environment = this.buildEnvironment(owner, "env");

        this.generateActivePoolForContents(owner, true, content1, content2, content3);
        this.promoteContentToEnvironments(content1, true, environment);
        this.promoteContentToEnvironments(content2, true, environment);

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        ContentAccessPayload payload1 = builder.setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getOwnerId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload1))
            .hasSize(2)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));

        // Impl note: we're leveraging knowledge that the consumer is not fully encapsulated, meaning changes
        // made to the consumer externally are reflected by the output of this builder.
        consumer.setFact(Consumer.Facts.ARCHITECTURE, "x86");

        ContentAccessPayload payload2 = builder.build();

        assertThat(payload2)
            .isNotNull()
            .returns(owner.getOwnerId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(payload1.getTimestamp(), ContentAccessPayload::getTimestamp)
            .doesNotReturn(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .doesNotReturn(payload1.getPayload(), ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload2))
            .hasSize(2)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadUsesConsumerSupportedArchitectures(Scheme scheme) throws Exception {
        // This test verifies that the consumer SUPPORTED_ARCHITECTURES fact is a component of the payload
        // key and generation

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");
        Content content4 = this.buildContent("RISC_V");
        Environment environment = this.buildEnvironment(owner, "env");

        this.generateActivePoolForContents(owner, true, content1, content2, content3, content4);
        this.promoteContentToEnvironments(content1, true, environment);
        this.promoteContentToEnvironments(content2, true, environment);

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        ContentAccessPayload payload1 = builder.setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload1))
            .hasSize(2)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));

        // Impl note: we're leveraging knowledge that the consumer is not fully encapsulated, meaning changes
        // made to the consumer externally are reflected by the output of this builder.
        consumer.setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "x86_64, x86");
        ContentAccessPayload payload2 = builder.build();

        assertThat(payload2)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(payload1.getTimestamp(), ContentAccessPayload::getTimestamp)
            .doesNotReturn(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .doesNotReturn(payload1.getPayload(), ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload2))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadIgnoresConsumerSupportedArchitecturesOrder(Scheme scheme) throws Exception {
        // This test verifies that the order of the environments is a critical component of the key or
        // payload. Even with identical inputs, the order of the environments changing should result in
        // different payloads.

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "x86, x86_64, ARMv3");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");
        Content content4 = this.buildContent("RISC_V");
        Environment environment = this.buildEnvironment(owner, "env");

        this.generateActivePoolForContents(owner, true, content1, content2, content3, content4);
        this.promoteContentToEnvironments(content1, true, environment);
        this.promoteContentToEnvironments(content2, true, environment);

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        ContentAccessPayload payload1 = builder.setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(environment))
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload1))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, environment))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, environment))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));

        // Impl note: we're leveraging knowledge that the consumer is not fully encapsulated, meaning changes
        // made to the consumer externally are reflected by the output of this builder.
        consumer.setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "ARMv3, x86_64, x86");
        ContentAccessPayload payload2 = builder.build();

        assertThat(payload2)
            .isNotNull()
            .returns(payload1.getOwnerId(), ContentAccessPayload::getOwnerId)
            .returns(payload1.getTimestamp(), ContentAccessPayload::getTimestamp)
            .returns(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .returns(payload1.getPayload(), ContentAccessPayload::getPayload);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadUsesEnvironments(Scheme scheme) throws Exception {
        // This test verifies that environments will change the output of the key and payload generation

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "x86");

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");
        Content content4 = this.buildContent("RISC_V");
        Environment env1 = this.buildEnvironment(owner, "env1");
        Environment env2 = this.buildEnvironment(owner, "env2");
        Environment env3 = this.buildEnvironment(owner, "env3");

        this.generateActivePoolForContents(owner, true, content1, content2, content3, content4);
        this.promoteContentToEnvironments(content1, true, env1);
        this.promoteContentToEnvironments(content2, true, env1);
        this.promoteContentToEnvironments(content3, true, env2);

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        ContentAccessPayload payload1 = builder.setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload1))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3));

        ContentAccessPayload payload2 = builder.setEnvironments(List.of(env1, env2, env3))
            .build();

        assertThat(payload2)
            .isNotNull()
            .returns(payload1.getOwnerId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .doesNotReturn(payload1.getTimestamp(), ContentAccessPayload::getTimestamp)
            .doesNotReturn(payload1.getPayload(), ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload2))
            .hasSize(3)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, env1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, env1))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3, env2));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadConsidersEnvironmentsOrder(Scheme scheme) throws Exception {
        // This test verifies that the order of the environments is not a component of the key or payload.
        // Even with identical inputs, the order of the environments changing should not result in different
        // payloads.

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");
        Content content4 = this.buildContent("RISC_V");
        Environment env1 = this.buildEnvironment(owner, "env1");
        Environment env2 = this.buildEnvironment(owner, "env2");
        Environment env3 = this.buildEnvironment(owner, "env3");

        this.generateActivePoolForContents(owner, true, content1, content2, content3, content4);
        this.promoteContentToEnvironments(content1, true, env1); // A
        this.promoteContentToEnvironments(content2, true, env1); //
        this.promoteContentToEnvironments(content2, true, env2); // B
        this.promoteContentToEnvironments(content3, true, env2); //
        this.promoteContentToEnvironments(content3, true, env3); // C
        this.promoteContentToEnvironments(content4, true, env3); //

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        ContentAccessPayload payload1 = builder.setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(env1, env2, env3))
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload1))
            .hasSize(4)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, env1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, env1))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3, env2))
            .hasEntrySatisfying(content4.getId(), this.hasPath(owner, content4, env3));

        ContentAccessPayload payload2 = builder.setEnvironments(List.of(env3, env2, env1))
            .build();

        assertThat(payload2)
            .isNotNull()
            .returns(payload1.getOwnerId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .doesNotReturn(payload1.getTimestamp(), ContentAccessPayload::getTimestamp)
            .doesNotReturn(payload1.getPayload(), ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload2))
            .hasSize(4)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, env1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, env2))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3, env3))
            .hasEntrySatisfying(content4.getId(), this.hasPath(owner, content4, env3));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testBuildPayloadIgnoresDuplicateEnvironments(Scheme scheme) throws Exception {
        // This test verifies that the duplicate environments are not considered when generating the payload
        // or key

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);

        Content content1 = this.buildContent(null);
        Content content2 = this.buildContent("x86");
        Content content3 = this.buildContent("x86_64");
        Content content4 = this.buildContent("RISC_V");
        Environment env1 = this.buildEnvironment(owner, "env1");
        Environment env2 = this.buildEnvironment(owner, "env2");
        Environment env3 = this.buildEnvironment(owner, "env3");

        this.generateActivePoolForContents(owner, true, content1, content2, content3, content4);
        this.promoteContentToEnvironments(content1, true, env1);
        this.promoteContentToEnvironments(content2, true, env1);
        this.promoteContentToEnvironments(content3, true, env2);

        ContentAccessPayloadBuilder builder = this.buildContentAccessPayloadBuilder();

        ContentAccessPayload payload1 = builder.setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of(env1, env2, env3))
            .build();

        assertThat(payload1)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));

        assertThat(this.mapPayloadContent(payload1))
            .hasSize(4)
            .hasEntrySatisfying(content1.getId(), this.hasPath(owner, content1, env1))
            .hasEntrySatisfying(content2.getId(), this.hasPath(owner, content2, env1))
            .hasEntrySatisfying(content3.getId(), this.hasPath(owner, content3, env2))
            .hasEntrySatisfying(content4.getId(), this.hasPath(owner, content4));

        ContentAccessPayload payload2 = builder.setEnvironments(List.of(env1, env2, env3, env1, env2, env3))
            .build();

        assertThat(payload2)
            .isNotNull()
            .returns(payload1.getOwnerId(), ContentAccessPayload::getOwnerId)
            .returns(payload1.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .returns(payload1.getTimestamp(), ContentAccessPayload::getTimestamp)
            .returns(payload1.getPayload(), ContentAccessPayload::getPayload)
            .satisfies(pl -> this.validatePayloadSignature(pl, scheme));
    }

}

