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

import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ExtensionWrapper;

import org.mozilla.jss.asn1.ASN1Util;
import org.mozilla.jss.asn1.OCTET_STRING;
import org.mozilla.jss.netscape.security.util.BitArray;
import org.mozilla.jss.netscape.security.util.DerInputStream;
import org.mozilla.jss.netscape.security.util.DerValue;
import org.mozilla.jss.netscape.security.x509.KeyIdentifier;

import java.io.IOException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * Default implementation of {@link SubjectKeyIdentifierWriter}. This implementation is exactly what you might
 * expect but hosted candlepin instances have use cases that require custom SubjectKeyIdentifiers so they may
 * write and bind other implementations of the SubjectKeyIdentifierWriter interface.
 *
 * @see SubjectKeyIdentifierWriter
 */
public class DefaultSubjectKeyIdentifierWriter implements SubjectKeyIdentifierWriter {

    @Override
    public byte[] getSubjectKeyIdentifier(KeyPair clientKeyPair, Set<X509ExtensionWrapper> extensions)
        throws IOException {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-1");

            byte[] encodedKey = clientKeyPair.getPublic().getEncoded();

            DerInputStream s = new DerValue(encodedKey).toDerInputStream();
            // Skip the first item in the sequence, AlgorithmIdentifier.
            // The parameter, startLen, is required for skipSequence although it's unused.
            s.skipSequence(0);

            // Get the key's bit string
            BitArray b = s.getUnalignedBitString();
            byte[] digest = d.digest(b.toByteArray());

            KeyIdentifier ki = new KeyIdentifier(digest);
            return ASN1Util.encode(new OCTET_STRING(ki.getIdentifier()));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IOException("Could not create KeyIdentifier", e);
        }
    }
}
