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
package org.candlepin.dto.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.candlepin.dto.api.server.v1.CryptographicSchemeDTO;
import org.candlepin.pki.CryptographyStatusProvider.SchemeMetadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CryptographicSchemeTranslatorTest {

    private CryptographicSchemeTranslator translator;

    @BeforeEach
    public void setUp() {
        this.translator = new CryptographicSchemeTranslator();
    }

    @Test
    public void testTranslateReturnsNullForNullSource() {
        assertThat(translator.translate(null))
            .isNull();
        assertThat(translator.translate(null, null))
            .isNull();
    }

    @Test
    public void testTranslateCreatesCorrectDTO() {
        SchemeMetadata source = new SchemeMetadata("rsa", "SHA256withRSA", "RSA", 4096);

        CryptographicSchemeDTO result = translator.translate(source);

        assertThat(result)
            .returns("rsa", CryptographicSchemeDTO::getName)
            .returns("SHA256withRSA", CryptographicSchemeDTO::getSignatureAlgorithm)
            .returns("RSA", CryptographicSchemeDTO::getKeyAlgorithm)
            .returns(4096, CryptographicSchemeDTO::getKeySize);
    }

    @Test
    public void testTranslateHandlesNullKeySize() {
        SchemeMetadata source = new SchemeMetadata("mldsa", "ML-DSA-87", "ML-DSA", null);

        CryptographicSchemeDTO result = translator.translate(source);

        assertThat(result)
            .isNotNull()
            .returns("mldsa", CryptographicSchemeDTO::getName)
            .returns("ML-DSA-87", CryptographicSchemeDTO::getSignatureAlgorithm)
            .returns("ML-DSA", CryptographicSchemeDTO::getKeyAlgorithm)
            .returns(null, CryptographicSchemeDTO::getKeySize);
    }

    @Test
    public void testPopulateThrowsOnNullSource() {
        CryptographicSchemeDTO dest = new CryptographicSchemeDTO();

        assertThatThrownBy(() -> translator.populate(null, dest))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testPopulateThrowsOnNullDestination() {
        SchemeMetadata source = new SchemeMetadata("rsa", "SHA256withRSA", "RSA", 4096);

        assertThatThrownBy(() -> translator.populate(source, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testPopulateUpdatesExistingDTO() {
        SchemeMetadata source = new SchemeMetadata("rsa", "SHA256withRSA", "RSA", 4096);
        CryptographicSchemeDTO dest = new CryptographicSchemeDTO();

        CryptographicSchemeDTO result = translator.populate(source, dest);

        assertThat(result)
            .isSameAs(dest)
            .returns("rsa", CryptographicSchemeDTO::getName)
            .returns("SHA256withRSA", CryptographicSchemeDTO::getSignatureAlgorithm)
            .returns("RSA", CryptographicSchemeDTO::getKeyAlgorithm)
            .returns(4096, CryptographicSchemeDTO::getKeySize);
    }

    @Test
    public void testPopulateWithTranslatorWorks() {
        SchemeMetadata source = new SchemeMetadata("rsa", "SHA256withRSA", "RSA", 4096);
        CryptographicSchemeDTO dest = new CryptographicSchemeDTO();

        // ModelTranslator is not needed for this simple translation, but should still work
        CryptographicSchemeDTO result = translator.populate(null, source, dest);

        assertThat(result)
            .isNotNull()
            .returns("rsa", CryptographicSchemeDTO::getName);
    }

    @Test
    public void testTranslateWithModelTranslatorWorks() {
        SchemeMetadata source = new SchemeMetadata("rsa", "SHA256withRSA", "RSA", 4096);

        // ModelTranslator can be null for this simple translation
        CryptographicSchemeDTO result = translator.translate(null, source);

        assertThat(result)
            .isNotNull()
            .returns("rsa", CryptographicSchemeDTO::getName)
            .returns("SHA256withRSA", CryptographicSchemeDTO::getSignatureAlgorithm)
            .returns("RSA", CryptographicSchemeDTO::getKeyAlgorithm)
            .returns(4096, CryptographicSchemeDTO::getKeySize);
    }
}
