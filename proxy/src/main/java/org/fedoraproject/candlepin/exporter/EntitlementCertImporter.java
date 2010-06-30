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
package org.fedoraproject.candlepin.exporter;

import java.io.IOException;
import java.io.Reader;

import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.pki.PKIUtility;

/**
 * EntitlementCertImporter
 */
public class EntitlementCertImporter {
    
    public EntitlementCertificate importObject(Reader reader) throws IOException {
        String certificateAndKey = StringFromReader.asString(reader);
        EntitlementCertificate toReturn = new EntitlementCertificate();
        toReturn.setCert(certificate(certificateAndKey));
        toReturn.setKey(key(certificateAndKey));
        // certificate has to be in DER format
        toReturn.setSerial(
            PKIUtility.createCert(certificate(certificateAndKey)).getSerialNumber());
        return toReturn;
    }
    
    private String certificate(String certificateAndKey) {
        return certificateAndKey.substring(
            certificateAndKey.indexOf(PKIUtility.BEGIN_CERTIFICATE), 
            certificateAndKey.indexOf(PKIUtility.END_CERTIFICATE) + 
                PKIUtility.END_CERTIFICATE.length());
    }
    
    private String key(String certificateAndKey) {
        return certificateAndKey.substring(
            certificateAndKey.indexOf(PKIUtility.BEGIN_KEY), 
            certificateAndKey.indexOf(PKIUtility.END_KEY) + 
                PKIUtility.END_KEY.length());
    }
}
