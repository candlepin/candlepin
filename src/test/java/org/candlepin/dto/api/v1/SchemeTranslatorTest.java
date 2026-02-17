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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.SchemeDTO;
import org.candlepin.pki.Scheme;

import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * Test suite for the SchemeTranslator class
 */
public class SchemeTranslatorTest extends AbstractTranslatorTest<Scheme, SchemeDTO, SchemeTranslator> {

    protected SchemeTranslator translator = new SchemeTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Scheme.class, SchemeDTO.class);
    }

    @Override
    protected SchemeTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Scheme initSourceObject() {
        X509Certificate mockCert = mock(X509Certificate.class);
        return new Scheme("rsa", mockCert, Optional.empty(), "SHA256withRSA", "RSA",
            Optional.of(4096));
    }

    @Override
    protected SchemeDTO initDestinationObject() {
        return new SchemeDTO();
    }

    @Override
    protected void verifyOutput(Scheme source, SchemeDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.name(), dest.getName());
            assertEquals(source.signatureAlgorithm(), dest.getSignatureAlgorithm());
            assertEquals(source.keyAlgorithm(), dest.getKeyAlgorithm());
            assertEquals(source.keySize().orElse(null), dest.getKeySize());
        }
        else {
            assertNull(dest);
        }
    }

    @Test
    public void testTranslateWithoutKeySize() {
        X509Certificate mockCert = mock(X509Certificate.class);
        Scheme source = new Scheme("mldsa", mockCert, Optional.empty(), "ML-DSA-87", "ML-DSA",
            Optional.empty());

        SchemeDTO dto = this.translator.translate(source);

        assertEquals(source.name(), dto.getName());
        assertEquals(source.signatureAlgorithm(), dto.getSignatureAlgorithm());
        assertEquals(source.keyAlgorithm(), dto.getKeyAlgorithm());
        assertNull(dto.getKeySize());
    }
}
