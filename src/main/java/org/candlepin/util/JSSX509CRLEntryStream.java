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
package org.candlepin.util;

import org.mozilla.jss.netscape.security.x509.RevokedCertImpl;
import org.mozilla.jss.netscape.security.x509.X509ExtensionException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.cert.CRLException;
import java.security.cert.X509CRLEntry;
import java.util.Date;
import java.util.Set;

/**
 * JSS based implementation of X509CRLEntryStream interface
 */
public class JSSX509CRLEntryStream extends X509CRLEntryStream {
    public JSSX509CRLEntryStream(InputStream stream) throws IOException {
        super(stream);
    }

    public JSSX509CRLEntryStream(File crlFile) throws IOException {
        super(crlFile);
    }

    @Override
    protected X509CRLEntry getX509CRLEntry(byte[] obj) throws IOException {
        try {
            /* RevokedCertImpl actually implements X509CRLEntry, but it contains this little gem:
             *
             * public boolean hasUnsupportedCriticalExtension() {
             *     // XXX NOT IMPLEMENTED
             *     return true;
             * }
             *
             * which is just asking for hard to diagnose runtime errors.  I'm going to return an
             * anonymous class that does the right thing and throws an UnsupportedOperationException.
             */
            RevokedCertImpl revokedCert = new RevokedCertImpl(obj);
            return new X509CRLEntry() {

                @Override
                public byte[] getEncoded() throws CRLException {
                    return revokedCert.getEncoded();
                }

                @Override
                public BigInteger getSerialNumber() {
                    return revokedCert.getSerialNumber();
                }

                @Override
                public Date getRevocationDate() {
                    return revokedCert.getRevocationDate();
                }

                @Override
                public boolean hasExtensions() {
                    return revokedCert.hasExtensions();
                }

                @Override
                public String toString() {
                    return revokedCert.toString();
                }

                @Override
                public boolean hasUnsupportedCriticalExtension() {
                    throw new UnsupportedOperationException("Not supported");
                }

                @Override
                public Set<String> getCriticalExtensionOIDs() {
                    return revokedCert.getCriticalExtensionOIDs();
                }

                @Override
                public Set<String> getNonCriticalExtensionOIDs() {
                    return revokedCert.getNonCriticalExtensionOIDs();
                }

                @Override
                public byte[] getExtensionValue(String oid) {
                    return revokedCert.getExtensionValue(oid);
                }
            };
        }
        catch (CRLException | X509ExtensionException e) {
            throw new IOException("Could not unmarshall CRL entry", e);
        }

    }

}
