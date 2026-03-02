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
package org.candlepin.pki.impl.jca;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.KeyPairGeneratorTest;
import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.KeyException;



/**
 * KeyPairGenerator test suite for the JcaKeyPairGenerator
 */
public class JcaKeyPairGeneratorTest extends KeyPairGeneratorTest {

    @Override
    protected KeyPairGenerator buildKeyPairGenerator(Scheme scheme) {
        return new JcaKeyPairGenerator(CryptoUtil.getSecurityProvider(), scheme);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGenerateKeyPairThrowsExceptionOnBadKeyAlgorithm(Scheme scheme) {
        Scheme malformed = new Scheme.Builder()
            .setName(scheme.name())
            .setPrivateKey(scheme.privateKey().orElse(null))
            .setCertificate(scheme.certificate())
            .setSignatureAlgorithm(scheme.signatureAlgorithm())
            .setKeyAlgorithm("malformed_key_algo")
            .setKeySize(scheme.keySize().orElse(null))
            .build();

        KeyPairGenerator generator = this.buildKeyPairGenerator(malformed);

        assertThrows(KeyException.class, () -> generator.generateKeyPair());
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGenerateKeyPairThrowsExceptionOnBadKeySize(Scheme scheme) {
        Scheme malformed = new Scheme.Builder()
            .setName(scheme.name())
            .setPrivateKey(scheme.privateKey().orElse(null))
            .setCertificate(scheme.certificate())
            .setSignatureAlgorithm(scheme.signatureAlgorithm())
            .setKeyAlgorithm(scheme.keyAlgorithm())
            // impl note: what constitutes "bad" is entirely algorithm and provider dependent. Our only real
            // hope for consistency here is throwing negative values at this.
            .setKeySize(-1)
            .build();

        KeyPairGenerator generator = this.buildKeyPairGenerator(malformed);

        assertThrows(KeyException.class, () -> generator.generateKeyPair());
    }

}
