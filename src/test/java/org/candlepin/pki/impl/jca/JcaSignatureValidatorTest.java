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

import org.candlepin.pki.Scheme;
import org.candlepin.pki.SignatureValidator;
import org.candlepin.pki.SignatureValidatorTest;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;



/**
 * Test suite for the JcaSignatureValidator when constructed with a scheme
 */
public class JcaSignatureValidatorTest extends SignatureValidatorTest {

    @Override
    protected SignatureValidator buildSignatureValidator(Scheme scheme) {
        return new JcaSignatureValidator(CryptoUtil.getSecurityProvider(), scheme);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testConstructionRequiresSecurityProvider(Scheme scheme) throws Exception {
        // Impl note: This could be a NPE or IllegalArgumentException depending on the underlying
        // implementation.
        assertThrows(NullPointerException.class, () -> new JcaSignatureValidator(null, scheme));
    }

    @Test
    public void testConstructionRequiresScheme() throws Exception {
        java.security.Provider provider = CryptoUtil.getSecurityProvider();
        assertThrows(NullPointerException.class, () -> new JcaSignatureValidator(provider, (Scheme) null));
    }

}
