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

import org.mozilla.jss.asn1.ASN1Util;
import org.mozilla.jss.asn1.OCTET_STRING;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * SubjectKeyIdentifierWriter is an interface to provide the encoded SubjectKeyIdentifier to a certificate.
 * The default implementation generates the SubjectKeyIdentifier normally.  Hosted Candlepin environments
 * can have different requirements for the SubjectKeyIdentifier, however.  For example, if a certificate
 * size is too large, certain content delivery providers may only send the SubjectKeyIdentifier extension
 * so it may be necessary to hijack the extension as an alternative communications channel.
 */
public interface SubjectKeyIdentifierWriter {
    /**
     * This method returns the SubjectKeyIdentifier extension that will be written into a certificate.
     * Note that this method is expected to return the <em>DER encoded KeyIdentifier</em> and not just the
     * KeyIdentifier bytes alone.
     * <p>
     * An extension consists of an OID, a boolean indicating whether the extension is critical or not, and
     * an ASN.1 octet string representing the extension value.  What's in the octet string varies from
     * extension to extension.  In the case of the SubjectKeyIdentifer, the KeyIdentifier itself is
     * DER encoded as an octet string.  The actual extension therefore looks like:
     * </p> <pre>
     * # No boolean for criticality is included since that defaults to false and this extension is
     * # required to be non-critical.
     * SEQUENCE {
     *   OBJECT IDENTIFIER subjectKeyIdentifier (2 5 29 14)
     *   OCTET STRING, encapsulates {
     *     OCTET STRING
     *       08 68 AF 85 33 C8 39 4A 7A F8 82 93 8E 70 6A 4A 20 84 2C 32
     *   }
     * }
     * </pre> <p>
     * In this case, the KeyIdentifier itself is the 20 byte 0x0868AF... value.  That value is encoded as
     * an octet string which will then be 22 bytes (1 byte for the tag, 0x04, and one byte for the length,
     * 0x14.  Those 22 bytes are then encoded in another octet string resulting in a total extension value
     * of 24 bytes (2 more bytes added to the beginning for the tag and length).
     * </p><p>
     * This method should return an DER encoded octet string of the KeyIdentifier, so in the example above,
     * the return value expected from this method is 0x04140868AF..., the 22 bytes of the internal octet
     * string.
     * </p><p>
     * Returning the encoded KeyIdentifier instead of just the KeyIdentifier is arguably dumb, but that's
     * how the API contract was written (and it made sense with the way BouncyCastle created extensions) so
     * we need to abide by it.
     * </p>
     * @param clientKeyPair the KeyPair of the certificate's subject
     * @param extensions Other Extensions being placed on this certificate
     * @return DER encoded octet string of the subject key identifier as a byte array
     * @throws IOException thrown if error reading extensions or encoding the KeyIdentifier
     */
    byte[] getSubjectKeyIdentifier(KeyPair clientKeyPair, Set<X509ExtensionWrapper> extensions)
        throws IOException, NoSuchAlgorithmException;

    default byte[] toOctetString(byte[] bytesToEncode) throws IOException {
        return ASN1Util.encode(new OCTET_STRING(bytesToEncode));
    }
}
