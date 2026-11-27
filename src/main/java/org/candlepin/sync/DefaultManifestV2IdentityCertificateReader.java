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

import org.candlepin.dto.manifest.v2.CertificateDTO;
import org.candlepin.dto.manifest.v2.CertificateSerialDTO;

import org.apache.commons.lang3.StringUtils;
import org.xnap.commons.i18n.I18n;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Detached decoding of the identity certificate exported under {@code upstream_consumer/<serial-id>.json}
 * <p>
 * Requires exactly one certificate JSON file alongside consumer.json, complete serial metadata, and non-blank
 * certificate and private key material. Cryptographic decoding, certificate ownership, and key matching remain
 * the certificate installer's responsibility. No database writes or local serial allocation are performed here.
 */
public class DefaultManifestV2IdentityCertificateReader implements ManifestV2IdentityCertificateReader {
    private final ObjectMapper mapper;
    private final I18n i18n;

    /**
     * Initializes identity certificate decoding dependencies
     *
     * @param mapper
     *     import mapper for v2 certificate DTOs
     * @param i18n
     *     import error translator
     * @throws NullPointerException
     *     if any dependency is null
     */
    @Inject
    public DefaultManifestV2IdentityCertificateReader(@Named("ImportObjectMapper") ObjectMapper mapper, I18n i18n) {
        this.mapper = Objects.requireNonNull(mapper);
        this.i18n = Objects.requireNonNull(i18n);
    }

    @Override
    public CertificateDTO read(Path manifestDirectory) throws ImporterException {
        Objects.requireNonNull(manifestDirectory);
        Path directory = manifestDirectory.resolve(ManifestConstants.UPSTREAM_CONSUMER);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw this.invalidCertificate();
        }
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> certificates = files
                .filter(file -> !ManifestConstants.CONSUMER_JSON.equals(file.getFileName().toString()))
                .toList();
            if (certificates.size() != 1) {
                throw this.invalidCertificate();
            }
            Path file = certificates.getFirst();
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw this.invalidCertificate();
            }
            CertificateDTO certificate;
            try (InputStream input = Files.newInputStream(file)) {
                certificate = this.mapper.readValue(input, CertificateDTO.class);
            }
            if (certificate == null || StringUtils.isBlank(certificate.getCertificate()) ||
                StringUtils.isBlank(certificate.getKey())) {
                throw this.invalidCertificate();
            }
            CertificateSerialDTO serial = certificate.getSerial();
            if (serial == null || serial.getId() == null || serial.getId() <= 0L || serial.getExpiration() == null ||
                !file.getFileName().toString().equals(String.format(ManifestConstants.ID_JSON, serial.getId()))) {
                throw this.invalidCertificate();
            }
            return certificate;
        }
        catch (IOException | JacksonException | IllegalArgumentException e) {
            // Parser exceptions can contain private key material; do not retain them as causes.
            throw this.invalidCertificate();
        }
    }

    private SyncDataFormatException invalidCertificate() {
        return new SyncDataFormatException(this.i18n.tr("Missing or invalid v2 identity certificate material"));
    }
}
