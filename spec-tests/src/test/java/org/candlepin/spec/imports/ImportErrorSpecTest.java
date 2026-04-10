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

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CryptographicCapabilitiesDTO;
import org.candlepin.dto.api.client.v1.ImportRecordDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UpstreamConsumerDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.CryptoCapabilities;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.CryptoUtil;
import org.candlepin.spec.bootstrap.data.util.ExportUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;



@SpecTest
@OnlyInStandalone
public class ImportErrorSpecTest {
    private static Stream<Arguments> capabilitiesSource() {
        return CryptoCapabilities.getSupportedCapabilities()
            .stream()
            .map(Arguments::of);
    }

    private static ApiClient adminClient;

    @BeforeAll
    public static void beforeAll() {
        assumeTrue(CandlepinMode::hasManifestGenTestExtension);

        adminClient = ApiClients.admin();
    }

    private AsyncJobStatusDTO importAsync(OwnerDTO owner, File manifest, String... force) {
        List<String> forced = force != null ? Arrays.asList(force) : List.of();

        AsyncJobStatusDTO importJob = adminClient.owners()
            .importManifestAsync(owner.getKey(), forced, manifest);

        return adminClient.jobs().waitForJob(importJob);
    }

    @ParameterizedTest(name = "[{index}] CryptographicCapabilitiesDTO")
    @MethodSource("capabilitiesSource")
    public void shouldReturnCorrectErrorStatusMessageOnADuplicateImport(
        CryptographicCapabilitiesDTO capabilities) throws Exception {

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        // Create a simple manifest to throw at the server twice
        File manifest = new ExportGenerator()
            .usingCryptographicCapabilities(capabilities)
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest);
        assertThatJob(importJob2)
            .isFailed()
            .contains("MANIFEST_SAME");

        List<ImportRecordDTO> importRecords = adminClient.owners().getImports(owner.getKey());

        assertThat(importRecords)
            .filteredOn(record -> "FAILURE".equalsIgnoreCase(record.getStatus()))
            .map(ImportRecordDTO::getStatusMessage)
            .contains("Import is the same as existing data");
    }

    @ParameterizedTest(name = "[{index}] CryptographicCapabilitiesDTO")
    @MethodSource("capabilitiesSource")
    public void shouldAllowForcingTheSameManifest(CryptographicCapabilitiesDTO capabilities)
        throws Exception {

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        // Create a simple manifest to throw at the server twice
        File manifest = new ExportGenerator()
            .usingCryptographicCapabilities(capabilities)
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS");

        // Attempting to import this manifest again should fail with "MANIFEST_SAME"
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest);
        assertThatJob(importJob2)
            .isFailed()
            .contains("MANIFEST_SAME");

        // ...but forcing it should succeed
        AsyncJobStatusDTO importJob3 = this.importAsync(owner, manifest, "MANIFEST_SAME");
        assertThatJob(importJob3)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");
    }

    @ParameterizedTest(name = "[{index}] CryptographicCapabilitiesDTO")
    @MethodSource("capabilitiesSource")
    public void shouldNotAllowImportingAnOldManifest(CryptographicCapabilitiesDTO capabilities)
        throws Exception {

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        ExportGenerator exportGenerator = new ExportGenerator();

        // Impl note: it's probably not strictly necessary to add or modify the subscription data
        // for this test, but it helps ensure we don't hit a "MANIFEST_SAME" error when we're not
        // looking for it.
        File oldManifest = exportGenerator
            .usingCryptographicCapabilities(capabilities)
            .addProduct(Products.random())
            .export();

        Thread.sleep(1500);

        File newManifest = exportGenerator.addProduct(Products.random())
            .export();

        // Import the new manifest first
        AsyncJobStatusDTO importJob1 = this.importAsync(owner, newManifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // Import the old manifest, which should be rejected for being older than the last manifest
        // we've imported.
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, oldManifest);
        assertThatJob(importJob2)
            .isFailed()
            .contains("MANIFEST_OLD");
    }

    @ParameterizedTest(name = "[{index}] CryptographicCapabilitiesDTO")
    @MethodSource("capabilitiesSource")
    public void shouldAllowImportingOlderManifestsIntoAnotherOwner(CryptographicCapabilitiesDTO capabilities)
        throws Exception {

        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());

        // Impl note: We cannot reuse the same generator here; the manifests must come from
        // different upstream orgs or we'll get a different, unrecoverable failure.
        File oldManifest = new ExportGenerator()
            .addProduct(Products.random())
            .usingCryptographicCapabilities(capabilities)
            .export();

        Thread.sleep(1500);

        File newManifest = new ExportGenerator()
            .addProduct(Products.random())
            .usingCryptographicCapabilities(capabilities)
            .export();

        // Import the new manifest to the first org
        AsyncJobStatusDTO importJob1 = this.importAsync(owner1, newManifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // Old Candlepin was blocking imports in multi-tenant deployments if one org imports
        // a manifest, and then another tries with a manifest that is even slightly
        // older. This tests that this restriction no longer applies.
        AsyncJobStatusDTO importJob2 = this.importAsync(owner2, oldManifest);
        assertThatJob(importJob2)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");
    }

    @ParameterizedTest(name = "[{index}] CryptographicCapabilitiesDTO")
    @MethodSource("capabilitiesSource")
    public void shouldReturnConflictWhenImportingManifestFromDifferentSubscriptionManagementApplication(
        CryptographicCapabilitiesDTO capabilities) throws Exception {

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest1 = new ExportGenerator()
            .usingCryptographicCapabilities(capabilities)
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest1);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // Impl note: we want the manifest to come from different upstream sources, so we can't
        // reuse the generator
        File manifest2 = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest2);
        assertThatJob(importJob2)
            .isFailed()
            .contains("DISTRIBUTOR_CONFLICT");
    }

    /**
     * Test is run sequentially to avoid conflicts with other tests during forced reimport.
     */
    @ParameterizedTest(name = "[{index}] CryptographicCapabilitiesDTO")
    @MethodSource("capabilitiesSource")
    public void shouldAllowForcingManifestFromDifferentSubscriptionManagementApplication(
        CryptographicCapabilitiesDTO capabilities) throws Exception {

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest1 = new ExportGenerator()
            .usingCryptographicCapabilities(capabilities)
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest1);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        OwnerDTO ownerRefresh1 = adminClient.owners().getOwner(owner.getKey());
        assertThat(ownerRefresh1)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNotNull()
            .extracting(UpstreamConsumerDTO::getUuid)
            .isNotNull();

        String upstreamConsumerUuid1 = ownerRefresh1.getUpstreamConsumer()
            .getUuid();

        // Impl note: we want the manifest to come from different upstream sources, so we can't
        // reuse the generator
        File manifest2 = new ExportGenerator()
            .usingCryptographicCapabilities(capabilities)
            .addProduct(Products.random())
            .export();

        // Importing manifest2 should fail since it has a different upstream source
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest2);
        assertThatJob(importJob2)
            .isFailed()
            .contains("DISTRIBUTOR_CONFLICT");

        // ...but forcing the distributor conflict should allow it to succeed without other issues
        AsyncJobStatusDTO importJob3 = this.importAsync(owner, manifest2, "DISTRIBUTOR_CONFLICT");
        assertThatJob(importJob3)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING")
            .contains("file imported forcibly");

        OwnerDTO ownerRefresh2 = adminClient.owners().getOwner(owner.getKey());
        assertThat(ownerRefresh2)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNotNull()
            .extracting(UpstreamConsumerDTO::getUuid)
            .isNotNull();

        String upstreamConsumerUuid2 = ownerRefresh2.getUpstreamConsumer()
            .getUuid();

        // The upstream consumer UUID after importing the second manifest should no longer match the
        // upstream consumer UUID of the first manifest.
        assertNotEquals(upstreamConsumerUuid1, upstreamConsumerUuid2);
    }

    @ParameterizedTest(name = "[{index}] CryptographicCapabilitiesDTO")
    @MethodSource("capabilitiesSource")
    public void shouldReturnBadRequestWhenImportingManifestInUseByAnotherOwner(
        CryptographicCapabilitiesDTO capabilities) throws Exception {

        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .usingCryptographicCapabilities(capabilities)
            .addProduct(Products.random())
            .export();

        // The first org should be able to import this manifest without issue
        AsyncJobStatusDTO importJob1 = this.importAsync(owner1, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // The second org should be rejected, as the manifest is already in use by another org on
        // this Candlepin instance
        AsyncJobStatusDTO importJob2 = this.importAsync(owner2, manifest);
        assertThatJob(importJob2)
            .isFailed()
            .contains("This subscription management application has already been imported by another owner");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "invalid" })
    public void shouldFailImportWithInvalidSignature(String signature) throws Exception {
        CryptographicCapabilitiesDTO capabilities = CryptoCapabilities.rsa();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .usingCryptographicCapabilities(capabilities)
            .addProduct(Products.random())
            .export();

        File modifiedManifest = this.replaceSignatureFile(manifest, signature.getBytes());
        String expectedMessage = signature.isBlank() ?
            "The archive does not contain the required signature file" :
            "Archive failed signature check";

        AsyncJobStatusDTO job = this.importAsync(owner, modifiedManifest);
        assertThatJob(job)
            .isFailed()
            .contains(expectedMessage);

        // We should be able to import a manifest with an invalid signature if we use the signature conflict
        // override
        if (!signature.isBlank()) {
            job = this.importAsync(owner, modifiedManifest, "SIGNATURE_CONFLICT");
            assertThatJob(job)
                .isFinished()
                .contains("SUCCESS");
        }
    }

    @Test
    public void shouldFailImportWithUntrustedCertificate() throws Exception {
        CryptographicCapabilitiesDTO capabilities = CryptoCapabilities.rsa();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .usingCryptographicCapabilities(capabilities)
            .addProduct(Products.random())
            .export();

        String signatureAlgorithm = "SHA256WithRSA";
        String keyAlgorithm = "rsa";
        int keySize = 4096;

        KeyPair keyPair = CryptoUtil.generateKeyPair(keyAlgorithm, keySize);
        X509Certificate selfSignedCert = CryptoUtil.generateX509Certificate(keyPair, signatureAlgorithm);
        File modifiedManifest = this.replaceCertificate(manifest, selfSignedCert);

        AsyncJobStatusDTO job = this.importAsync(owner, modifiedManifest);
        assertThatJob(job)
            .isFailed()
            .contains("Archive signed by an untrusted certificate");

        // We should no longer fail the untrusted signature verification. We will still fail the signature
        // verification, because we did not update the signature after modifiying the consumer export zip.
        job = this.importAsync(owner, modifiedManifest, "UNTRUSTED_SIGNING_CERTIFICATE");
        assertThatJob(job)
            .isFailed()
            .contains("Archive failed signature check");
    }

    private File replaceSignatureFile(File manifest, byte[] signature) throws IOException {
        Path outputPath = Files.createTempFile("modified-manifest", ".zip");
        File modifiedManifest = outputPath.toFile();
        modifiedManifest.deleteOnExit();

        try (ZipFile zip = new ZipFile(manifest);
            ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath))) {
            for (ZipEntry entry : zip.stream().toList()) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                if (ExportUtil.SIGNATURE_FILENAME.equals(entry.getName())) {
                    zos.write(signature);
                }
                else {
                    try (InputStream istream = zip.getInputStream(entry)) {
                        istream.transferTo(zos);
                    }
                }
            }
        }

        return modifiedManifest;
    }

    private File replaceCertificate(File manifest, X509Certificate certificate)
        throws IOException, CertificateEncodingException {

        String schemePath = "export/" + ExportUtil.SCHEME_FILENAME;
        Path outputPath = Files.createTempFile("modified-manifest", ".zip");
        File modifiedManifest = outputPath.toFile();
        modifiedManifest.deleteOnExit();

        File originalConsumerExport = Files.createTempFile("consumer_export_orig", ".zip")
            .toFile();
        originalConsumerExport.deleteOnExit();

        File modifiedConsumerExport = Files.createTempFile("consumer_export_modified", ".zip").toFile();
        modifiedConsumerExport.deleteOnExit();

        try (ZipFile outer = new ZipFile(manifest)) {
            ZipEntry consumerExportEntry = outer.getEntry(ExportUtil.EXPORT_NAME);
            try (InputStream is = outer.getInputStream(consumerExportEntry);
                FileOutputStream fos = new FileOutputStream(originalConsumerExport)) {
                is.transferTo(fos);
            }

            try (ZipFile consumerExport = new ZipFile(originalConsumerExport);
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(modifiedConsumerExport))) {
                for (ZipEntry entry : consumerExport.stream().toList()) {
                    zos.putNextEntry(new ZipEntry(entry));
                    if (!entry.isDirectory()) {
                        // Replace the certificate
                        if (schemePath.equals(entry.getName())) {
                            ObjectNode scheme = (ObjectNode) ApiClient.MAPPER
                                .readTree(consumerExport.getInputStream(entry));
                            String encodedCert = Base64.getEncoder()
                                .encodeToString(certificate.getEncoded());
                            scheme.put("certificate", encodedCert);
                            zos.write(ApiClient.MAPPER.writeValueAsBytes(scheme));
                        }
                        else {
                            try (InputStream entryStream = consumerExport.getInputStream(entry)) {
                                entryStream.transferTo(zos);
                            }
                        }
                    }
                }
            }

            try (ZipOutputStream outerZos = new ZipOutputStream(Files.newOutputStream(outputPath))) {
                for (ZipEntry entry : outer.stream().toList()) {
                    outerZos.putNextEntry(new ZipEntry(entry.getName()));
                    if (ExportUtil.EXPORT_NAME.equals(entry.getName())) {
                        try (InputStream is = new FileInputStream(modifiedConsumerExport)) {
                            is.transferTo(outerZos);
                        }
                    }
                    else if (!entry.isDirectory()) {
                        try (InputStream is = outer.getInputStream(entry)) {
                            is.transferTo(outerZos);
                        }
                    }

                    outerZos.closeEntry();
                }
            }
        }

        return modifiedManifest;
    }

}
