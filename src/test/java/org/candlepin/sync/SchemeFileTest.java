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

import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.stream.Stream;

public class SchemeFileTest {
    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    @Test
    public void testFromWithNullScheme() {
        assertThrows(IllegalArgumentException.class,
            () -> SchemeFile.from(null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testFrom(Scheme scheme) throws Exception {
        Encoder encoder = Base64.getEncoder();
        String encodedCert = encoder.encodeToString(scheme.certificate().getEncoded());

        SchemeFile schemeFile = SchemeFile.from(scheme);

        assertThat(schemeFile)
            .isNotNull()
            .returns(scheme.name(), SchemeFile::name)
            .returns(encodedCert, SchemeFile::certificate)
            .returns(scheme.signatureAlgorithm(), SchemeFile::signatureAlgorithm)
            .returns(scheme.keyAlgorithm(), SchemeFile::keyAlgorithm);
    }

}
