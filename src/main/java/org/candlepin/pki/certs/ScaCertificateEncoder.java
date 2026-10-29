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

import org.candlepin.model.dto.Content;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.OID;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.huffman.Huffman;
import org.candlepin.util.X509V3ExtensionUtil;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Encoding and signing of SCA X.509 certificates from prepared data
 */
@Singleton
public class ScaCertificateEncoder {
    private final CryptoManager cryptoManager;
    private final PemEncoder pemEncoder;
    private final Huffman huffman;

    @Inject
    public ScaCertificateEncoder(CryptoManager cryptoManager, PemEncoder pemEncoder, Huffman huffman) {
        this.cryptoManager = Objects.requireNonNull(cryptoManager);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.huffman = Objects.requireNonNull(huffman);
    }

    /**
     * Encodes a signed certificate using prepared subject and access paths
     *
     * @return PEM-encoded X.509 certificate
     */
    /* pkg-private */ String encode(ScaCertificateData data, Scheme scheme, BigInteger serial,
        KeyPair keyPair, Instant start, Instant end) {

        Objects.requireNonNull(data);
        Objects.requireNonNull(scheme);
        Objects.requireNonNull(serial);
        Objects.requireNonNull(keyPair);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);

        return this.pemEncoder.encodeAsString(this.cryptoManager.getCertificateBuilder(scheme)
            .withDN(data.subject())
            .withSerial(serial)
            .withValidity(start, end)
            .withKeyPair(keyPair)
            .withExtensions(this.prepareExtensions(data.accessPaths()))
            .build());
    }

    private Set<X509Extension> prepareExtensions(List<String> paths) {
        List<Content> content = paths.stream()
            .map(path -> new Content().setPath(path))
            .toList();

        try {
            Set<X509Extension> extensions = new HashSet<>();
            extensions.add(new X509StringExtension(OID.EntitlementVersion.namespace(), X509V3ExtensionUtil.CERT_VERSION));
            extensions.add(new X509StringExtension(OID.EntitlementType.namespace(), "OrgLevel"));
            extensions.add(new X509ByteExtension(OID.EntitlementData.namespace(), this.huffman.retrieveContentValue(content)));
            return extensions;
        }
        catch (IOException e) {
            throw new CertificateCreationException("Failed to prepare extensions", e);
        }
    }
}
