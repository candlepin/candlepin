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
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Detached decoding of the SCA certificate exported under {@code sca_cert.json}
 * <p>
 * Requires the SCA certificate JSON file, complete serial metadata, and non-blank certificate and private key
 * material. Cryptographic decoding, certificate ownership, and key matching remain the certificate installer's
 * responsibility. No database writes or local serial allocation are performed here.
 */
public class DefaultManifestV2ScaCertificateReader implements ManifestV2ScaCertificateReader {
    private final ObjectMapper mapper;
    private final I18n i18n;

    /**
     * Initializes SCA certificate decoding dependencies
     *
     * @param mapper
     *     import mapper for v2 certificate DTOs
     * @param i18n
     *     import error translator
     * @throws NullPointerException
     *     if any dependency is null
     */
    @Inject
    public DefaultManifestV2ScaCertificateReader(@Named("ImportObjectMapper") ObjectMapper mapper, I18n i18n) {
        this.mapper = Objects.requireNonNull(mapper);
        this.i18n = Objects.requireNonNull(i18n);
    }

    @Override
    public CertificateDTO read(Path manifestDirectory) throws ImporterException {
        Objects.requireNonNull(manifestDirectory);
        Path file = manifestDirectory.resolve(ManifestConstants.SCA_CERTIFICATE_JSON);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw this.invalidCertificate();
        }
        try (InputStream input = Files.newInputStream(file)) {
            CertificateDTO certificate = this.mapper.readValue(input, CertificateDTO.class);
            if (certificate == null || StringUtils.isBlank(certificate.getCertificate()) ||
                StringUtils.isBlank(certificate.getKey())) {
                throw this.invalidCertificate();
            }
            CertificateSerialDTO serial = certificate.getSerial();
            if (serial == null || serial.getId() == null || serial.getId() <= 0L || serial.getExpiration() == null) {
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
        return new SyncDataFormatException(this.i18n.tr("Missing or invalid v2 SCA certificate material"));
    }
}
