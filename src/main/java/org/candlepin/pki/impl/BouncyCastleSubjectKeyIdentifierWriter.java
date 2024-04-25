/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509Extension;

import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.List;



/**
 * Default implementation of SubjectKeyIdentifierWriter.  This implementation is exactly what you might
 * expect but hosted Candlepin instances have use cases that require custom SubjectKeyIdentifiers so they may
 * write and bind other implementations of the SubjectKeyIdentifierWriter interface.
 */
public class BouncyCastleSubjectKeyIdentifierWriter implements SubjectKeyIdentifierWriter {

    @Override
    public byte[] getSubjectKeyIdentifier(KeyPair clientKeyPair, List<X509Extension> extensions)
        throws IOException, NoSuchAlgorithmException {

        return new JcaX509ExtensionUtils()
            .createSubjectKeyIdentifier(clientKeyPair.getPublic())
            .getEncoded();
    }
}
