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
package org.candlepin.spec.bootstrap.assertions;

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.CryptographicCapabilitiesDTO;
import org.candlepin.dto.api.client.v1.UeberCertificateDTO;

import org.assertj.core.api.AbstractObjectAssert;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.StringReader;
import java.security.PrivateKey;
import java.util.Optional;



public class PrivateKeyAssert extends AbstractObjectAssert<PrivateKeyAssert, PrivateKey> {

    public PrivateKeyAssert(PrivateKey key) {
        super(key, PrivateKeyAssert.class);
    }

    /**
     * Attempts to read the private key from the given key data. The key data is assumed to be well-formed
     * PEM-encoded PKCS#8 data. If the key data is null, this method returns null.
     *
     * @param keyData
     *  a string containing the key data to convert to a PrivateKey instance
     *
     * @return
     *  a PrivateKey created from the provided key data, or null if no data was provided
     */
    private static PrivateKey readPrivateKey(String keyData) {
        if (keyData == null) {
            return null;
        }

        try (PEMParser parser = new PEMParser(new StringReader(keyData))) {
            PrivateKeyInfo pkinfo = PrivateKeyInfo.getInstance(parser.readObject());

            return new JcaPEMKeyConverter()
                .getPrivateKey(pkinfo);
        }
        catch (IllegalArgumentException | IOException e) {
            throw new AssertionError("Private key data is not valid PKCS#8", e);
        }
    }

    public static PrivateKeyAssert assertThatKey(String keyData) {
        PrivateKey key = readPrivateKey(keyData);

        return new PrivateKeyAssert(key);
    }

    public static PrivateKeyAssert assertThatKey(CertificateDTO container) {
        PrivateKey key = Optional.ofNullable(container)
            .map(CertificateDTO::getKey)
            .map(PrivateKeyAssert::readPrivateKey)
            .orElse(null);

        return new PrivateKeyAssert(key);
    }

    public static PrivateKeyAssert assertThatKey(UeberCertificateDTO container) {
        PrivateKey key = Optional.ofNullable(container)
            .map(UeberCertificateDTO::getKey)
            .map(PrivateKeyAssert::readPrivateKey)
            .orElse(null);

        return new PrivateKeyAssert(key);
    }

    private String getAlgorithmOid() {
        byte[] bytes = this.actual.getEncoded();

        return PrivateKeyInfo.getInstance(bytes)
            .getPrivateKeyAlgorithm() // AlgorithmIdentifier
            .getAlgorithm() // ASN1ObjectIdentifier
            .getId();
    }

    public PrivateKeyAssert usesAlgorithmMatchingCapabilities(CryptographicCapabilitiesDTO caps) {
        if (this.actual == null) {
            this.failWithMessage("private key is null");
        }

        // If the capabilities are null (legacy) or do not define key algorithms, then we match
        if (caps == null || caps.getKeyAlgorithms() == null) {
            return this;
        }

        // Grab the private key's algorithmn OID
        String algorithmOid = this.getAlgorithmOid();

        // Otherwise, ensure the algo OID is present in the capabilities
        if (!caps.getKeyAlgorithms().contains(algorithmOid)) {
            this.failWithMessage("Expected key algorithm OID \"%s\" to be present in capabilities: %s",
                algorithmOid, caps.getKeyAlgorithms());
        }

        return this;
    }

}
