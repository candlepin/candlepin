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

package org.candlepin.spec.imports;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UpstreamConsumerDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.ExportUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * End-to-end spec tests for Manifest V2 import
 */
@SpecTest
@OnlyInStandalone
public class ImportV2SpecTest {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @TempDir
    private Path directory;

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
        "2.0.0, Missing or empty v2 signing material",
        "3.0.0, Unsupported manifest format version"
    })
    public void rejectsUnsignedV2OrUnsupportedVersionWithoutModifyingOwner(String version, String message)
        throws Exception {
        ApiClient client = ApiClients.admin();
        OwnerDTO owner = client.owners().createOwner(Owners.randomSca());
        Path archive = this.directory.resolve("manifest.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("meta.json"));
            zip.write(("{\"manifestVersion\":\"" + version + "\",\"version\":\"6.0.0\"}")
                .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        AsyncJobStatusDTO job = client.owners().importManifestAsync(owner.getKey(), List.of(), archive.toFile());
        job = client.jobs().waitForJob(job);
        assertThatJob(job).isFailed().contains(message);
        assertThat(client.owners().getOwner(owner.getKey()).getUpstreamConsumer()).isNull();
        assertThat(client.pools().listPoolsByOwner(owner.getId())).isEmpty();
        assertThat(client.owners().getImports(owner.getKey()))
            .anySatisfy(record -> {
                assertThat(record.getStatus()).isEqualTo("FAILURE");
                assertThat(record.getStatusMessage()).contains(message);
            });
    }

    @Test
    public void shouldImportSignedV2ManifestAndReconcilePoolsOnReimport() throws Exception {
        ApiClient client = ApiClients.admin();
        OwnerDTO owner = client.owners().createOwner(Owners.randomSca());

        ProductDTO customProduct = client.ownerProducts().createProduct(owner.getKey(), Products.random());
        PoolDTO customPool = client.owners().createPool(owner.getKey(), Pools.random(customProduct));

        String upstreamUuid = StringUtil.random("upstream-v2-");
        String contentId = StringUtil.random("content-v2-");
        String providedProdId = StringUtil.random("provided-v2-");
        String derivedProdId = StringUtil.random("derived-v2-");
        String rootProd1Id = StringUtil.random("root1-v2-");
        String rootProd2Id = StringUtil.random("root2-v2-");
        String upstreamPool1Id = StringUtil.random("upool1-");
        String upstreamPool2Id = StringUtil.random("upool2-");

        long idSerial1 = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        long scaSerial1 = idSerial1 + 1L;
        Instant firstCreated = Instant.now().minus(2, ChronoUnit.HOURS);

        Map<String, Object> products1 = Map.of(
            providedProdId, this.productMap(providedProdId, "Provided V2", null, List.of(), Map.of()),
            derivedProdId, this.productMap(derivedProdId, "Derived V2", null, List.of(), Map.of()),
            rootProd1Id, this.productMap(rootProd1Id, "Root 1 V2", derivedProdId, List.of(providedProdId),
                Map.of(contentId, this.contentMap(contentId))),
            rootProd2Id, this.productMap(rootProd2Id, "Root 2 V2", null, List.of(), Map.of())
        );
        Map<String, Object> subs1 = Map.of(
            upstreamPool1Id, this.subscriptionMap("sub-1", rootProd1Id, "contract-1", "account-1"),
            upstreamPool2Id, this.subscriptionMap("sub-2", rootProd2Id, "contract-2", "account-2")
        );

        File manifest1 = this.createSignedV2Archive("manifest1.zip", upstreamUuid, firstCreated,
            idSerial1, scaSerial1, products1, subs1);

        // Without UNTRUSTED_SIGNING_CERTIFICATE override, untrusted signer is rejected
        AsyncJobStatusDTO rejectedJob = client.owners()
            .importManifestAsync(owner.getKey(), List.of(), manifest1);
        rejectedJob = client.jobs().waitForJob(rejectedJob);
        assertThatJob(rejectedJob).isFailed().contains("Archive signed by an untrusted certificate");
        assertThat(client.owners().getOwner(owner.getKey()).getUpstreamConsumer()).isNull();

        // With UNTRUSTED_SIGNING_CERTIFICATE override, full v2 import succeeds
        AsyncJobStatusDTO job1 = client.owners().importManifestAsync(
            owner.getKey(), List.of("UNTRUSTED_SIGNING_CERTIFICATE"), manifest1);
        job1 = client.jobs().waitForJob(job1);
        assertThatJob(job1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        OwnerDTO updatedOwner = client.owners().getOwner(owner.getKey());
        UpstreamConsumerDTO upstream = updatedOwner.getUpstreamConsumer();
        assertThat(upstream)
            .isNotNull()
            .returns(upstreamUuid, UpstreamConsumerDTO::getUuid)
            .extracting(UpstreamConsumerDTO::getIdCert)
            .isNotNull();
        assertThat(upstream.getIdCert().getSerial().getId())
            .isEqualTo(idSerial1);

        List<PoolDTO> poolsAfterFirst = client.pools().listPoolsByOwner(owner.getId());
        assertThat(poolsAfterFirst)
            .hasSize(3)
            .map(PoolDTO::getId)
            .contains(customPool.getId());

        PoolDTO importedPool1 = poolsAfterFirst.stream()
            .filter(p -> upstreamPool1Id.equals(p.getUpstreamPoolId()))
            .findFirst()
            .orElseThrow();
        assertThat(importedPool1.getQuantity()).isEqualTo(-1L);
        assertThat(importedPool1.getDerivedProductId()).isEqualTo(derivedProdId);
        assertThat(importedPool1)
            .extracting(PoolDTO::getProvidedProducts, as(collection(ProvidedProductDTO.class)))
            .singleElement()
            .extracting(ProvidedProductDTO::getProductId)
            .isEqualTo(providedProdId);

        ContentDTO importedContent = client.ownerContent().getContentById(owner.getKey(), contentId);
        assertThat(importedContent)
            .isNotNull()
            .returns(1L, ContentDTO::getMetadataExpire);

        assertThat(client.owners().getImports(owner.getKey()))
            .anySatisfy(record -> assertThat(record.getStatus()).isEqualTo("SUCCESS"));

        // Re-import with only upstreamPool1Id (removing upstreamPool2Id) from the same upstream consumer
        long idSerial2 = idSerial1 + 2L;
        File manifest2 = this.createSignedV2Archive("manifest2.zip", upstreamUuid,
            Instant.now().minus(1, ChronoUnit.HOURS), idSerial2, idSerial1 + 3L, products1,
            Map.of(upstreamPool1Id, this.subscriptionMap("sub-1-upd", rootProd1Id, "contract-1-upd", "account-1")));

        AsyncJobStatusDTO job2 = client.owners().importManifestAsync(
            owner.getKey(), List.of("UNTRUSTED_SIGNING_CERTIFICATE"), manifest2);
        assertThatJob(client.jobs().waitForJob(job2)).isFinished().contains("SUCCESS");

        List<PoolDTO> poolsAfterSecond = client.pools().listPoolsByOwner(owner.getId());
        assertThat(poolsAfterSecond).hasSize(2).map(PoolDTO::getId).contains(customPool.getId());
        PoolDTO reconciledPool1 = poolsAfterSecond.stream()
            .filter(p -> upstreamPool1Id.equals(p.getUpstreamPoolId()))
            .findFirst()
            .orElseThrow();
        assertThat(reconciledPool1.getSubscriptionId()).isEqualTo(importedPool1.getSubscriptionId());
        assertThat(reconciledPool1.getContractNumber()).isEqualTo("contract-1-upd");
        assertThat(client.owners().getOwner(owner.getKey()).getUpstreamConsumer().getIdCert().getSerial().getId())
            .isEqualTo(idSerial2);
    }

    @Test
    public void shouldImportServerGeneratedV2ManifestWithoutOverrides() throws Exception {
        assumeTrue(CandlepinMode::hasManifestGenTestExtension);

        ApiClient client = ApiClients.admin();
        OwnerDTO owner = client.owners().createOwner(Owners.randomSca());
        ProductDTO product = Products.random();
        SubscriptionDTO sub = Subscriptions.random().product(product);

        File manifest = new ExportGenerator()
            .addSubscription(sub)
            .exportV2();

        long exportedIdSerial = this.readExportedIdentitySerial(manifest);

        AsyncJobStatusDTO job = client.owners().importManifestAsync(owner.getKey(), List.of(), manifest);
        job = client.jobs().waitForJob(job);
        assertThatJob(job)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        OwnerDTO updatedOwner = client.owners().getOwner(owner.getKey());
        assertThat(updatedOwner.getUpstreamConsumer().getIdCert().getSerial().getId())
            .isEqualTo(exportedIdSerial);
        assertThat(client.pools().listPoolsByOwner(owner.getId()))
            .singleElement()
            .returns(sub.getId(), PoolDTO::getUpstreamPoolId)
            .returns(-1L, PoolDTO::getQuantity);
    }

    private File createSignedV2Archive(String filename, String consumerUuid, Instant created,
        long idSerial, long scaSerial, Map<String, Object> products, Map<String, Object> subscriptions)
        throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(2048);
        KeyPair signingKeys = keyGen.generateKeyPair();
        X509Certificate signingCert = this.generateCertificate(signingKeys, "cp.manifest.signer", 999999L);

        Map<String, byte[]> innerEntries = new LinkedHashMap<>();
        innerEntries.put("scheme.json", this.mapper.writeValueAsBytes(Map.of(
            "name", "rsa",
            "certificate", Base64.getEncoder().encodeToString(signingCert.getEncoded()),
            "key_algorithm", "RSA",
            "signature_algorithm", "SHA256WithRSA"
        )));
        innerEntries.put("meta.json", this.mapper.writeValueAsBytes(Map.of(
            "manifestVersion", "2.0.0",
            "version", "6.0.0-1",
            "created", Date.from(created),
            "principalName", "admin",
            "webAppPrefix", "https://upstream.example.com/subscription"
        )));
        innerEntries.put("upstream_consumer/consumer.json", this.mapper.writeValueAsBytes(Map.of(
            "uuid", consumerUuid,
            "name", "upstream-" + consumerUuid,
            "ownerId", "upstream-owner",
            "urlApi", "https://upstream.example.com/candlepin",
            "urlWeb", "https://upstream.example.com/subscription",
            "type", Map.of("id", "candlepin-type", "label", "candlepin")
        )));
        innerEntries.put("upstream_consumer/" + idSerial + ".json",
            this.mapper.writeValueAsBytes(this.createCertDtoMap(keyGen, consumerUuid, idSerial, false)));
        innerEntries.put("sca_cert.json",
            this.mapper.writeValueAsBytes(this.createCertDtoMap(keyGen, consumerUuid, scaSerial, true)));

        for (Map.Entry<String, Object> entry : products.entrySet()) {
            innerEntries.put("products/" + entry.getKey() + ".json",
                this.mapper.writeValueAsBytes(entry.getValue()));
        }
        for (Map.Entry<String, Object> entry : subscriptions.entrySet()) {
            innerEntries.put("subscriptions/" + entry.getKey() + ".json",
                this.mapper.writeValueAsBytes(entry.getValue()));
        }

        byte[] innerZip = this.zipBytes(innerEntries);
        Signature signer = Signature.getInstance("SHA256WithRSA", "BC");
        signer.initSign(signingKeys.getPrivate());
        signer.update(innerZip);
        byte[] signature = signer.sign();

        Path archivePath = this.directory.resolve(filename);
        Files.write(archivePath, this.zipBytes(Map.of(
            "consumer_export.zip", innerZip,
            "signature", signature
        )));
        return archivePath.toFile();
    }

    private Map<String, Object> createCertDtoMap(KeyPairGenerator keyGen, String cn, long serialId, boolean sca)
        throws Exception {
        KeyPair keyPair = keyGen.generateKeyPair();
        X509Certificate cert = this.generateCertificate(keyPair, cn, serialId);
        String certPem = this.toPem(cert);
        if (sca) {
            certPem = certPem +
                "-----BEGIN ENTITLEMENT DATA-----\npayload\n-----END ENTITLEMENT DATA-----\n" +
                "-----BEGIN SIGNATURE-----\nsignature\n-----END SIGNATURE-----\n";
        }
        Date now = new Date();
        Date exp = Date.from(Instant.now().plus(365, ChronoUnit.DAYS));
        return Map.of(
            "id", "cert-" + serialId,
            "cert", certPem,
            "key", this.toPem(keyPair.getPrivate()),
            "created", now,
            "updated", now,
            "serial", Map.of(
                "id", serialId,
                "serial", serialId,
                "expiration", exp,
                "revoked", false,
                "created", now,
                "updated", now
            )
        );
    }

    private X509Certificate generateCertificate(KeyPair keyPair, String cn, long serialId) throws Exception {
        Instant now = Instant.now();
        X500Name dn = new X500Name("CN=" + cn);
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
            dn,
            BigInteger.valueOf(serialId),
            Date.from(now.minus(1, ChronoUnit.HOURS)),
            Date.from(now.plus(365, ChronoUnit.DAYS)),
            dn,
            SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider("BC")
            .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(builder.build(signer));
    }

    private String toPem(Object obj) throws Exception {
        StringWriter writer = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(obj);
        }
        return writer.toString();
    }

    private Map<String, Object> contentMap(String id) {
        return Map.of(
            "id", id,
            "name", "Content " + id,
            "label", "label-" + id,
            "type", "yum",
            "vendor", "Red Hat",
            "contentUrl", "/content/" + id,
            "arches", "x86_64",
            "enabled", true
        );
    }

    private Map<String, Object> productMap(String id, String name, String derivedId,
        List<String> providedIds, Map<String, Object> content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("created", new Date(1000L));
        map.put("updated", new Date(2000L));
        map.put("attributes", Map.of("arch", "x86_64"));
        map.put("derivedProduct", derivedId);
        map.put("providedProducts", providedIds);
        map.put("productContent", content);
        map.put("branding", List.of());
        return map;
    }

    private Map<String, Object> subscriptionMap(String subId, String productId, String contract, String account) {
        Date start = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date end = Date.from(Instant.now().plus(30, ChronoUnit.DAYS));
        return Map.of(
            "subscriptionId", subId,
            "productId", productId,
            "contractNumber", contract,
            "accountNumber", account,
            "startDate", start,
            "endDate", end,
            "created", start,
            "updated", start
        );
    }

    private byte[] zipBytes(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private long readExportedIdentitySerial(File manifest) throws Exception {
        try (FileSystem outer = FileSystems.newFileSystem(manifest.toPath());
            FileSystem inner = FileSystems.newFileSystem(outer.getPath(ExportUtil.EXPORT_NAME));
            Stream<Path> files = Files.list(inner.getPath("upstream_consumer"))) {
            Path idCertFile = files
                .filter(p -> !p.getFileName().toString().equals("consumer.json"))
                .findFirst()
                .orElseThrow();
            JsonNode json = this.mapper.readTree(Files.readString(idCertFile));
            return json.get("serial").get("id").asLong();
        }
    }
}
