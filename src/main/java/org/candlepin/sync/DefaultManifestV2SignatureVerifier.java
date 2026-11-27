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

import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;

import org.xnap.commons.i18n.I18n;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Trust and detached signature verification of the signed archive produced by {@link ExporterV2}
 * <p>
 * Scheme metadata and signature bytes are read from the original upload, never from unsigned sibling files.
 * Missing or malformed signing material cannot be overridden. Trust and signature conflicts use the same
 * explicit overrides as legacy imports. The caller must supply the unchanged payload extracted from this archive.
 */
public class DefaultManifestV2SignatureVerifier implements ManifestV2SignatureVerifier {
    private final ObjectMapper mapper;
    private final CryptoManager cryptoManager;
    private final CertificateReader certificateReader;
    private final I18n i18n;

    /**
     * Initializes signature verification dependencies
     *
     * @param mapper
     *     import mapper for scheme metadata
     * @param cryptoManager
     *     signing certificate trust and signature validation services
     * @param certificateReader
     *     X.509 decoder for the scheme certificate
     * @param i18n
     *     import error translator
     * @throws NullPointerException
     *     if any dependency is null
     */
    @Inject
    public DefaultManifestV2SignatureVerifier(@Named("ImportObjectMapper") ObjectMapper mapper,
        CryptoManager cryptoManager, CertificateReader certificateReader, I18n i18n) {
        this.mapper = Objects.requireNonNull(mapper);
        this.cryptoManager = Objects.requireNonNull(cryptoManager);
        this.certificateReader = Objects.requireNonNull(certificateReader);
        this.i18n = Objects.requireNonNull(i18n);
    }

    @Override
    public void verify(Path archive, Path manifestDirectory, ConflictOverrides overrides) throws ImporterException {
        Objects.requireNonNull(archive);
        Objects.requireNonNull(manifestDirectory);
        Objects.requireNonNull(overrides);
        if (Files.exists(manifestDirectory.resolve(ManifestConstants.CONSUMER_EXPORT_ZIP)) ||
            Files.exists(manifestDirectory.resolve(ManifestConstants.SIGNATURE))) {
            throw new SyncDataFormatException(this.i18n.tr("The v2 payload must be extracted separately from its envelope"));
        }
        try (FileSystem outer = FileSystems.newFileSystem(archive)) {
            Path payloadPath = this.requiredFile(outer, ManifestConstants.CONSUMER_EXPORT_ZIP);
            byte[] payload = Files.readAllBytes(payloadPath);
            byte[] signature = Files.readAllBytes(this.requiredFile(outer, ManifestConstants.SIGNATURE));
            SchemeFile metadata;
            try (FileSystem inner = FileSystems.newFileSystem(payloadPath)) {
                metadata = this.mapper.readValue(Files.readAllBytes(this.requiredFile(inner, ManifestConstants.SCHEME_JSON)),
                    SchemeFile.class);
            }
            if (metadata == null) {
                throw new SyncDataFormatException(this.i18n.tr("The v2 manifest has invalid scheme metadata"));
            }
            X509Certificate certificate = this.certificateReader.read(new ByteArrayInputStream(
                Base64.getDecoder().decode(metadata.certificate())));
            Scheme scheme = new Scheme.Builder()
                .setName(metadata.name())
                .setCertificate(certificate)
                .setKeyAlgorithm(metadata.keyAlgorithm())
                .setSignatureAlgorithm(metadata.signatureAlgorithm())
                .build();
            if (!this.cryptoManager.isTrustedCertificate(certificate) &&
                !overrides.isForced(Importer.Conflict.UNTRUSTED_SIGNING_CERTIFICATE)) {
                throw new ImportConflictException(this.i18n.tr("Archive signed by an untrusted certificate"),
                    Importer.Conflict.UNTRUSTED_SIGNING_CERTIFICATE);
            }
            boolean valid = this.cryptoManager.getSignatureValidator(scheme)
                .forSignature(signature)
                .validate(payload);
            if (!valid && !overrides.isForced(Importer.Conflict.SIGNATURE_CONFLICT)) {
                throw new ImportConflictException(this.i18n.tr("Archive failed signature check"),
                    Importer.Conflict.SIGNATURE_CONFLICT);
            }
        }
        catch (IOException | CertificateException | JacksonException | IllegalArgumentException |
            ProviderNotFoundException e) {
            throw new ImporterException(this.i18n.tr("Unable to read or verify v2 manifest signature"), e);
        }
    }

    private Path requiredFile(FileSystem archive, String name) throws IOException, SyncDataFormatException {
        Path file = archive.getPath("/", name);
        if (!Files.isRegularFile(file) || Files.size(file) == 0L) {
            throw new SyncDataFormatException(this.i18n.tr("Missing or empty v2 signing material"));
        }
        return file;
    }
}
