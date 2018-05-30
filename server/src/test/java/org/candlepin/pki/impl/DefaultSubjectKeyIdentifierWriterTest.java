/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import static org.junit.Assert.*;

import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;


public class DefaultSubjectKeyIdentifierWriterTest {

    private KeyPair keyPair;

    @Before
    public void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    public void getSubjectKeyIdentifier() throws Exception {
        DefaultSubjectKeyIdentifierWriter writer = new DefaultSubjectKeyIdentifierWriter();
        byte[] actual = writer.getSubjectKeyIdentifier(keyPair, null);
        byte[] expected = new JcaX509ExtensionUtils()
            .createSubjectKeyIdentifier(keyPair.getPublic())
            .getEncoded();

        assertArrayEquals(expected, actual);
    }
}
