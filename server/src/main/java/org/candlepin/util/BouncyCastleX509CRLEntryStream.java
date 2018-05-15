/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.candlepin.util.DERUtil.*;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.TBSCertList.CRLEntry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.cert.CRLException;
import java.security.cert.X509CRLEntry;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements X509CRLEntryStream using the BouncyCastle crypto provider.
 */
public class BouncyCastleX509CRLEntryStream extends X509CRLEntryStream {
    public BouncyCastleX509CRLEntryStream(InputStream stream) throws IOException {
        super(stream);
    }

    public BouncyCastleX509CRLEntryStream(File crlFile) throws IOException {
        super(crlFile);
    }

    @Override
    protected X509CRLEntry getX509CRLEntry(byte[] obj) throws IOException {
        CRLEntry bcEntry = CRLEntry.getInstance(obj);
        return new X509CRLEntry() {

            @Override
            public byte[] getEncoded() throws CRLException {
                try {
                    return bcEntry.getEncoded();
                }
                catch (IOException e) {
                    throw new CRLException("Could not encode entry", e);
                }
            }

            @Override
            public BigInteger getSerialNumber() {
                return bcEntry.getUserCertificate().getValue();
            }

            @Override
            public Date getRevocationDate() {
                return bcEntry.getRevocationDate().getDate();
            }

            @Override
            public boolean hasExtensions() {
                return bcEntry.hasExtensions();
            }

            @Override
            public String toString() {
                return bcEntry.toString();
            }

            @Override
            public boolean hasUnsupportedCriticalExtension() {
                /* We don't use this method anywhere and I don't know which critical extensions we should
                 * consider "supported", so skip this. */
                throw new UnsupportedOperationException("Currently not provided functionality");
            }

            @Override
            public Set<String> getCriticalExtensionOIDs() {
                ASN1ObjectIdentifier[] oids = bcEntry.getExtensions().getCriticalExtensionOIDs();
                return Arrays.stream(oids).map(x -> x.toString()).collect(Collectors.toSet());
            }

            @Override
            public Set<String> getNonCriticalExtensionOIDs() {
                ASN1ObjectIdentifier[] oids = bcEntry.getExtensions().getNonCriticalExtensionOIDs();
                return Arrays.stream(oids).map(x -> x.toString()).collect(Collectors.toSet());
            }

            @Override
            public byte[] getExtensionValue(String oid) {
                try {
                    return bcEntry.getExtensions().getExtension(ASN1ObjectIdentifier.getInstance(oid))
                        .getEncoded();
                }
                catch (IOException e) {
                    throw new RuntimeException("Could not read extension", e);
                }
            }
        };
    }
}
