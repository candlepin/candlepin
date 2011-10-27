/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.cert.CertificateParsingException;
import java.util.Set;

import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ExtensionWrapper;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;

/**
 * SubjectKeyIdentifierWriter
 */
public class DefaultSubjectKeyIdentifierWriter implements SubjectKeyIdentifierWriter {

    @Override
    public DEREncodable getSubjectKeyIdentifier(KeyPair clientKeyPair,
                                                Set<X509ExtensionWrapper> extensions)
        throws CertificateParsingException, IOException, InvalidKeyException {

        return new SubjectKeyIdentifierStructure(clientKeyPair.getPublic());
    }

}
