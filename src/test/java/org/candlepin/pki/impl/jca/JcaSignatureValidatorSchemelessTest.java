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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.pki.Scheme;
import org.candlepin.pki.SignatureValidator;
import org.candlepin.pki.SignatureValidatorTest;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;



/**
 * Test suite for the JcaSignatureValidator when constructed without a scheme
 */
public class JcaSignatureValidatorSchemelessTest extends SignatureValidatorTest {

    @Override
    protected SignatureValidator buildSignatureValidator(Scheme scheme) {
        return new JcaSignatureValidator(CryptoUtil.getSecurityProvider(), scheme.signatureAlgorithm())
            .withAdditionalCertificates(List.of(scheme.certificate()));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testConstructionRequiresSecurityProvider(Scheme scheme) throws Exception {
        // Impl note: This could be a NPE or IllegalArgumentException depending on the underlying
        // implementation.
        assertThrows(NullPointerException.class, () -> new JcaSignatureValidator(null, scheme));
        assertThrows(NullPointerException.class,
            () -> new JcaSignatureValidator(null, scheme.signatureAlgorithm()));
    }

    @Test
    public void testConstructionRequiresAlgorithm() throws Exception {
        java.security.Provider provider = CryptoUtil.getSecurityProvider();
        assertThrows(NullPointerException.class, () -> new JcaSignatureValidator(provider, (String) null));
    }

    @Override
    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testConstructionRetainsScheme(Scheme scheme) throws Exception {
        // This is a sort of negative test validating the behavior that will occur when this object is
        // constructed without a scheme. We have to override this test from the base test suite since it's a
        // violation of the spec.
        SignatureValidator validator = this.buildSignatureValidator(scheme);

        assertNull(validator.getCryptoScheme());
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testSchemelessValidationWithBytesFailsValidationWithoutCert(Scheme scheme) throws Exception {
        // This test verifies that the expected behavior is non-validation rather than an exception when no
        // certs are provided.

        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(scheme.signatureAlgorithm(), scheme.privateKey().get(), bytes);

        SignatureValidator validator = new JcaSignatureValidator(CryptoUtil.getSecurityProvider(),
            scheme.signatureAlgorithm())
            .forSignature(signature);

        assertFalse(validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testSchemelessValidationWithFileFailsValidationWithoutCert(Scheme scheme) throws Exception {
        // This test verifies that the expected behavior is non-validation rather than an exception when no
        // certs are provided.

        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(scheme.signatureAlgorithm(), scheme.privateKey().get(), bytes);

        SignatureValidator validator = new JcaSignatureValidator(CryptoUtil.getSecurityProvider(),
            scheme.signatureAlgorithm())
            .forSignature(signature);

        assertFalse(validator.validate(file));
    }

}
