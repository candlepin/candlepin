/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bouncycastle.util.io.pem.PemGenerationException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

class BouncyCastlePemEncoderTest {

    @Test
    void shouldEncodeKey() throws NoSuchAlgorithmException {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);
        KeyPair keyPair = generator.generateKeyPair();

        String result = encoder.encodeAsString(keyPair.getPrivate());

        assertThat(result)
            .startsWith("-----BEGIN RSA PRIVATE KEY-----")
            .endsWith("-----END RSA PRIVATE KEY-----\n");
    }

    @Test
    void shouldEncodeCert() throws NoSuchAlgorithmException {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);
        KeyPair keyPair = generator.generateKeyPair();

        String result = encoder.encodeAsString(keyPair.getPrivate());

        assertThat(result)
            .startsWith("-----BEGIN RSA PRIVATE KEY-----")
            .endsWith("-----END RSA PRIVATE KEY-----\n");
    }

    @Test
    void shouldFailForInvalidObjects() {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();

        assertThatThrownBy(() -> encoder.encodeAsBytes("Invalid"))
            .isInstanceOf(PemEncodingException.class);
        assertThatThrownBy(() -> encoder.encodeAsString("Invalid"))
            .isInstanceOf(PemEncodingException.class);
    }

    @Test
    void shouldFailForInvalidNulls() {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();

        assertThatThrownBy(() -> encoder.encodeAsBytes(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encodeAsString(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
