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
package org.candlepin.pki;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * SubjectKeyIdentifierWriter is an interface to write the SubjectKeyIdentifier extension to a certificate.
 * The default implementation generates the SubjectKeyIdentifier extension normally.  Hosted Candlepin
 * environments can have different requirements for the SubjectKeyIdentifier, however.  For example, if a
 * certificate size is too large, certain content delivery providers may only send the SubjectKeyIdentifier
 * extension so it may be necessary to hijack the extension as an alternative communications channel.
 */
public interface SubjectKeyIdentifierWriter {
    /**
     * This method returns the SubjectKeyIdentifier extension that will be written into a certificate.
     *
     * @param clientKeyPair
     * @param extensions
     * @return DER encoded subject key identifier as a byte array
     * @throws IOException thrown if error reading cert
     * @throws NoSuchAlgorithmException thrown if JcaX509ExtensionUtils can't be created
     */
    byte[] getSubjectKeyIdentifier(KeyPair clientKeyPair, Set<X509ExtensionWrapper> extensions)
        throws IOException, NoSuchAlgorithmException;
}
