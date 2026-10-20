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

package org.candlepin.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.manifest.v2.ConsumerDTO;
import org.candlepin.dto.manifest.v2.MetaDTO;
import org.candlepin.dto.manifest.v2.ProductDTO;
import org.candlepin.dto.manifest.v2.SubscriptionDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.CryptoCapabilitiesException;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.certs.CertificateCreationException;
import org.candlepin.pki.certs.ConcurrentContentPayloadCreationException;
import org.candlepin.pki.certs.SCACertificateGenerator;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyException;
import java.security.KeyPair;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class ExporterV2Test {

    public static final String TEST_EXPORT_USER = "testExportUser";
    @Mock
    private PrincipalProvider principalProvider;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private EnvironmentCurator environmentCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private PoolCurator poolCurator;
    @Mock
    private ProductCurator productCurator;

    @Mock
    private SCACertificateGenerator scaCertificateGenerator;
    @TempDir
    private Path workDirectory;

    private DevConfig config;
    private ObjectMapper mapper;
    private CryptoManager cryptoManager;
    private ModelTranslator modelTranslator;

    private final ConsumerType systemType = new ConsumerType("system").setId("ct-1");

    @BeforeEach
    void setUp() {
        Principal principal = Mockito.mock(Principal.class);

        this.config = TestConfig.defaults();
        this.mapper = ObjectMapperFactory.getSyncObjectMapper(this.config);
        this.cryptoManager = CryptoUtil.getCryptoManager(this.config);
        this.modelTranslator = new StandardTranslator(consumerTypeCurator, environmentCurator, ownerCurator,
            this.config);

        this.config.setProperty(ConfigProperties.SYNC_WORK_DIR, this.workDirectory.toString());

        when(principalProvider.get()).thenReturn(principal);
        when(principal.getName()).thenReturn(TEST_EXPORT_USER);

        when(consumerTypeCurator.getConsumerType(any())).thenReturn(systemType);
    }

    @Test
    public void testExportV2() throws
        ExportCreationException, CryptoCapabilitiesException, ConcurrentContentPayloadCreationException {
        config.setProperty(ConfigProperties.EXPORT_FILTER_NON_GLOBAL_NAMESPACE, "true");

        Owner owner = TestUtil.createOwner("Example-Corporation");

        Product prod12345 = TestUtil.createProduct("12345", "RHEL Product");
        prod12345.setMultiplier(1L);
        prod12345.setCreated(new Date());
        prod12345.setUpdated(new Date());
        prod12345.setAttributes(Collections.emptyMap());
        prod12345.setProductContent(List.of(
            new ProductContent(
                prod12345,
                new Content("content-12345")
                    .setContentUrl("context-12345/url")
                    .setName("Content-12345")
                    .setNamespace("ns-12345"),
                true)
        ));

        Product mktProd = TestUtil.createProduct("MKT-prod", "RHEL Product");
        mktProd.setMultiplier(1L);
        mktProd.setCreated(new Date());
        mktProd.setUpdated(new Date());
        mktProd.setAttributes(Collections.emptyMap());

        Product mktSubProd = TestUtil.createProduct("MKT-sub-prod", "Sub Product");
        mktSubProd.setMultiplier(1L);
        mktSubProd.setCreated(new Date());
        mktSubProd.setUpdated(new Date());
        mktSubProd.setAttributes(Collections.emptyMap());

        Product subProvidedProduct332211 = TestUtil.createProduct("332211", "Sub Product");
        subProvidedProduct332211.setMultiplier(1L);
        subProvidedProduct332211.setCreated(new Date());
        subProvidedProduct332211.setUpdated(new Date());
        subProvidedProduct332211.setAttributes(Collections.emptyMap());

        Product subNamespacedProduct = TestUtil.createProduct("namespaced-product", "Namespaced Product");
        subNamespacedProduct.setMultiplier(1L);
        subNamespacedProduct.setCreated(new Date());
        subNamespacedProduct.setUpdated(new Date());
        subNamespacedProduct.setNamespace("namespaced");
        subNamespacedProduct.setAttributes(Collections.emptyMap());

        Content sharedContent = new Content("shared-content");
        mktProd.addContent(sharedContent, true);
        mktSubProd.addContent(sharedContent, false);
        mktProd.addProvidedProduct(prod12345);
        mktProd.addProvidedProduct(subNamespacedProduct);
        mktProd.addProvidedProduct(subProvidedProduct332211);
        mktProd.setDerivedProduct(mktSubProd);
        mktSubProd.addProvidedProduct(subProvidedProduct332211);

        Pool pool = TestUtil.createPool(owner)
            .setId("MockedPoolId")
            .setProduct(mktProd)
            .setSubscriptionId("subscription");

        when(this.poolCurator.listByOwnerForExportV2(owner.getId(), true, Pool.PoolType.NORMAL))
            .thenReturn(List.of(pool));
        when(this.productCurator.getProductsByIdsRecursiveForExportV2(true, List.of(mktProd.getId())))
            .thenReturn(List.of(mktProd, mktSubProd, prod12345, subProvidedProduct332211));

        Consumer consumer = createCertificateConsumer(owner);

        SCACertificate storedCertificate = new SCACertificate();
        storedCertificate.setCert("stored consumer certificate");
        SCACertificate certificate = createTestCertificate(consumer, storedCertificate);

        ExporterV2 exporter = this.createExporter();
        Path export = exporter.getFullExport(consumer);

        verify(this.scaCertificateGenerator).generateScaCertificate(eq(consumer), argThat(content ->
            content.size() == 2 &&
                content.stream().allMatch(link -> link.getContent() == sharedContent) &&
                content.stream().anyMatch(ProductContent::isEnabled) &&
                content.stream().anyMatch(link -> !link.isEnabled())));
        assertNotNull(export);
        assertTrue(Files.exists(export));
        assertTrue(Files.isRegularFile(export));

        Verify.existsIn(export)
            .verify(Paths.get(ExporterV2.CERTIFICATES_DIRECTORY, certificate.getSerial().getSerial() + ".pem"));

        Path metaPath = Paths.get("meta.json");
        Verify.existsIn(export)
            .verify(metaPath);
        Verify.content(export, MetaDTO.class,
            metaDto -> {
                assertEquals(TEST_EXPORT_USER, metaDto.getPrincipalName());
                assertEquals("2", metaDto.getManifestVersion());
            })
            .verify(metaPath);

        verifyConsumer(export, consumer);

        verifySubscription(export, pool);

        Verify.isMissing(export)
            .verify(Paths.get(ExporterV2.PRODUCTS_DIRECTORY, subNamespacedProduct.getUuid() + ".json"));

        verifyProduct(export, prod12345);
        verifyProduct(export, mktProd);
        verifyProduct(export, subProvidedProduct332211);
        verifyProduct(export, mktSubProd);

        exporter.deleteArchive(export);
    }

    private SCACertificate createTestCertificate(Consumer consumer, SCACertificate storedCertificate)
        throws ConcurrentContentPayloadCreationException,
        CryptoCapabilitiesException {
        consumer.setContentAccessCert(storedCertificate);
        CertificateSerial serial = new CertificateSerial();
        serial.setId(42L);
        SCACertificate certificate = new SCACertificate();
        certificate.setSerial(serial);
        certificate.setCert("-----BEGIN CERTIFICATE-----\ncertificate\n-----END CERTIFICATE-----\n" +
            "-----BEGIN ENTITLEMENT DATA-----\npayload\n-----END ENTITLEMENT DATA-----\n" +
            "-----BEGIN SIGNATURE-----\nsignature\n-----END SIGNATURE-----\n");
        certificate.setKey("-----BEGIN PRIVATE KEY-----\nkey\n-----END PRIVATE KEY-----\n");
        when(this.scaCertificateGenerator.generateScaCertificate(eq(consumer), anyList())).thenReturn(certificate);
        return certificate;
    }

    @Test
    void exportsCertificateAndKeyUnderSerialId() throws Exception {
        Owner owner = TestUtil.createOwner("Example-Corporation");
        Consumer consumer = this.createCertificateConsumer(owner);
        SCACertificate storedCertificate = new SCACertificate();
        storedCertificate.setCert("stored consumer certificate");
        consumer.setContentAccessCert(storedCertificate);
        CertificateSerial serial = new CertificateSerial();
        serial.setId(42L);
        SCACertificate certificate = new SCACertificate();
        certificate.setSerial(serial);
        certificate.setCert("-----BEGIN CERTIFICATE-----\ncertificate\n-----END CERTIFICATE-----\n" +
            "-----BEGIN ENTITLEMENT DATA-----\npayload\n-----END ENTITLEMENT DATA-----\n" +
            "-----BEGIN SIGNATURE-----\nsignature\n-----END SIGNATURE-----\n");
        certificate.setKey("-----BEGIN PRIVATE KEY-----\nkey\n-----END PRIVATE KEY-----\n");
        when(this.scaCertificateGenerator.generateScaCertificate(eq(consumer), anyList())).thenReturn(certificate);

        Path export = this.createExporter()
            .getFullExport(consumer);

        try (FileSystem archive = FileSystems.newFileSystem(export)) {
            Path directory = archive.getPath(ExporterV2.CERTIFICATES_DIRECTORY);
            assertEquals(certificate.getCert() + certificate.getKey(), Files.readString(directory.resolve("42.pem")));
            try (Stream<Path> files = Files.list(directory)) {
                assertEquals(List.of("42.pem"), files.map(path -> path.getFileName()
                        .toString())
                    .toList());
            }
            assertTrue(Files.exists(archive.getPath(ExporterV2.META_JSON)));
            assertTrue(Files.exists(archive.getPath(ExporterV2.UPSTREAM_CONSUMER, ExporterV2.CONSUMER_JSON)));
        }
        verify(this.scaCertificateGenerator).generateScaCertificate(eq(consumer), anyList());
        assertSame(storedCertificate, consumer.getContentAccessCert());
        assertEquals("stored consumer certificate", storedCertificate.getCert());
    }

    @Test
    void omitsCertificateDirectoryWhenGeneratorReturnsNull() throws Exception {
        Owner owner = TestUtil.createOwner("Example-Corporation");
        Consumer consumer = this.createCertificateConsumer(owner);
        when(this.scaCertificateGenerator.generateScaCertificate(eq(consumer), anyList()))
            .thenThrow(CertificateCreationException.class);

        assertThrows(ExportCreationException.class,
            () -> {
                this.createExporter()
                    .getFullExport(consumer);
            });
    }

    private static Stream<Exception> certificateGenerationFailures() {
        return Stream.of(new CryptoCapabilitiesException("unsupported scheme"),
            new ConcurrentContentPayloadCreationException("concurrent payload creation"));
    }

    @ParameterizedTest
    @MethodSource("certificateGenerationFailures")
    void removesArchiveWhenCertificateGenerationFails(Exception failure) throws Exception {
        Owner owner = TestUtil.createOwner("Example-Corporation");
        Consumer consumer = this.createCertificateConsumer(owner);
        when(this.scaCertificateGenerator.generateScaCertificate(eq(consumer), anyList())).thenThrow(failure);

        ExportCreationException actual = assertThrows(ExportCreationException.class,
            () -> this.createExporter()
                .getFullExport(consumer));

        assertSame(failure, actual.getCause());
        assertEquals("Unable to create export archive", actual.getMessage());
        verify(this.scaCertificateGenerator).generateScaCertificate(eq(consumer), anyList());
        try (Stream<Path> files = Files.list(this.workDirectory)) {
            assertFalse(files.findAny()
                .isPresent());
        }
    }

    private Consumer createCertificateConsumer(Owner owner) {
        return new Consumer()
            .setOwner(owner)
            .setType(this.systemType)
            .setName("Export consumer")
            .setUuid("export-consumer");
    }
    private java.util.function.Consumer<ConsumerDTO> createConsumerVerifier(Consumer consumer) {
        return dto -> {
            assertEquals(consumer.getUuid(), dto.getUuid());
            assertEquals(consumer.getName(), dto.getName());
            assertEquals(consumer.getOwner().getId(), dto.getOwnerId());
        };
    }

    private java.util.function.Consumer<SubscriptionDTO> createSubscriptionVerifier(Pool pool) {
        return dto -> {
            assertEquals(pool.getSubscriptionId(), dto.getSubscriptionId());
            assertEquals(pool.getAccountNumber(), dto.getAccountNumber());
            assertEquals(pool.getContractNumber(), dto.getContractNumber());
            assertEquals(pool.getProductId(), dto.getProductId());
            assertEquals(pool.getStartDate(), dto.getStartDate());
            assertEquals(pool.getEndDate(), dto.getEndDate());
        };
    }

    private java.util.function.Consumer<ProductDTO> createProductVerifier(Product product) {
        return dto -> {
            assertEquals(product.getId(), dto.getId());
            assertEquals(product.getName(), dto.getName());
            boolean filterNamespaces = this.config.getBoolean(ConfigProperties.EXPORT_FILTER_NON_GLOBAL_NAMESPACE);
            Set<String> expectedProvidedProducts = product.getProvidedProducts().stream()
                .filter(provided -> !filterNamespaces || provided.getNamespace() == null ||
                    provided.getNamespace().isBlank())
                .map(Product::getId)
                .collect(Collectors.toSet());
            Set<String> actualProvidedProducts = dto.getProvidedProducts();
            assertEquals(expectedProvidedProducts, actualProvidedProducts);
            Set<String> expectedContent = product.getProductContent().stream()
                .map(ProductContent::getContent)
                .filter(content -> !filterNamespaces || content.getNamespace() == null ||
                    content.getNamespace().isBlank())
                .map(Content::getId)
                .collect(Collectors.toSet());
            assertEquals(expectedContent, dto.getProductContent().keySet());
        };
    }

    private void verifyConsumer(Path archive, Consumer consumer) {
        Path consumerPath = Paths.get(ExporterV2.UPSTREAM_CONSUMER, ExporterV2.CONSUMER_JSON);
        Verify.existsIn(archive)
            .verify(consumerPath);
        Verify.content(archive, ConsumerDTO.class, createConsumerVerifier(consumer))
            .verify(consumerPath);
    }

    private void verifySubscription(Path archive, Pool pool) {
        Path subscriptionPath = Paths.get(ExporterV2.SUBSCRIPTIONS_DIRECTORY, pool.getSubscriptionId() + ".json");
        Verify.existsIn(archive)
            .verify(subscriptionPath);
        Verify.content(archive, SubscriptionDTO.class, createSubscriptionVerifier(pool))
            .verify(subscriptionPath);
    }

    private void verifyProduct(Path archive, Product product) {
        Path productPath = Paths.get(ExporterV2.PRODUCTS_DIRECTORY, product.getId() + ".json");
        Verify.existsIn(archive)
            .verify(productPath);
        Verify.content(archive, ProductDTO.class, createProductVerifier(product))
            .verify(productPath);
    }

    @FunctionalInterface
    interface Verify {
        void verify(Path path);

        static Verify verifyOn(Path archive, Verify test) {
            return path -> {
                try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + archive.toUri()), Map.of())) {
                    test.verify(fs.getPath(fs.getSeparator())
                        .resolve(path.toString()));
                }
                catch (IOException e) {
                    throw new RuntimeException(String.format("Error in test function on archive '%s'", archive), e);
                }
            };
        }

        static Verify existsIn(Path archive) {
            return verifyOn(archive, path -> assertTrue(Files.exists(path)));
        }

        static Verify isMissing(Path archive) {
            return verifyOn(archive, path -> assertFalse(Files.exists(path)));
        }

        static <T> Verify content(Path archive, Class<T> type, java.util.function.Consumer<T> test) {
            return verifyOn(archive, path -> {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    ObjectMapper mapper = ObjectMapperFactory.getObjectMapper();
                    T content = mapper.readValue(reader, type);
                    test.accept(content);
                }
                catch (IOException e) {
                    throw new RuntimeException(
                        String.format("Error in test function on archive '%s' file '%s'", archive, path),
                        e);
                }
            });
        }
    }

    private ExporterV2 createExporter() {
        return new ExporterV2(
            mapper,
            config,
            poolCurator,
            productCurator,
            principalProvider,
            modelTranslator,
            this.scaCertificateGenerator
        );
    }

    private KeyPairData generateConsumerKeyPairData(Scheme scheme) throws KeyException {
        if (scheme == null) {
            scheme = this.cryptoManager.getDefaultCryptoScheme();
        }

        KeyPair keypair = CryptoUtil.generateKeyPair(scheme);

        return new KeyPairData()
            .setPublicKeyData(keypair.getPublic()
                .getEncoded())
            .setPrivateKeyData(keypair.getPrivate()
                .getEncoded())
            .setAlgorithm(scheme.keyAlgorithm());
    }
}
