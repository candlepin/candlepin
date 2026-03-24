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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.sync.SignatureFile.SignatureScheme;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.stream.Stream;

public class SignatureFileTest {
    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    private CryptoManager cryptoManager;

    @BeforeEach
    public void beforeEach() {
        this.cryptoManager = CryptoUtil.getCryptoManager();
    }

    @Test
    public void testConstructorWithNullScheme() {
        String signature = TestUtil.randomString("signature-");

        assertThrows(IllegalArgumentException.class, () -> new SignatureFile(null, signature));
    }

    @Test
    public void testConstructorWithNullSignature() {
        SignatureScheme scheme = new SignatureScheme("name", "cert", "sig-algo", "cert-algo");

        assertThrows(IllegalArgumentException.class, () -> new SignatureFile(scheme, null));
    }

    @Test
    public void testFromWithNullScheme() {
        byte[] signature = new byte[0];

        assertThrows(IllegalArgumentException.class, () -> SignatureFile.from(null, signature));
    }

    @Test
    public void testFromWithNullSignature() {
        Scheme scheme = this.cryptoManager.getDefaultCryptoScheme();

        assertThrows(IllegalArgumentException.class, () -> SignatureFile.from(scheme, null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testFrom(Scheme scheme) throws Exception {
        String signature = TestUtil.randomString("signature-");
        Encoder encoder = Base64.getEncoder();
        String encodedSignature = encoder.encodeToString(signature.getBytes());
        String encodedCert = encoder.encodeToString(scheme.certificate().getEncoded());

        SignatureFile signatureFile = SignatureFile.from(scheme, signature.getBytes());

        assertThat(signatureFile)
            .isNotNull()
            .returns(encodedSignature, SignatureFile::signature)
            .extracting(SignatureFile::scheme)
            .returns(scheme.name(), SignatureScheme::name)
            .returns(encodedCert, SignatureScheme::certificate)
            .returns(scheme.signatureAlgorithm(), SignatureScheme::signatureAlgorithm)
            .returns(scheme.keyAlgorithm(), SignatureScheme::keyAlgorithm);
    }

}
