/**
 * Copyright (c) 2010 Red Hat, Inc.
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
package org.fedoraproject.candlepin.cert;

import org.bouncycastle.asn1.ASN1Encodable;

/**
 * 
 * X509ExtensionWrapper
 */
public class X509ExtensionWrapper {
    private String oid = null;
    private boolean critical;
    private ASN1Encodable asn1Encodable;
    
    public X509ExtensionWrapper(String oid, boolean critical, ASN1Encodable asn1Encodable) {
        this.oid = oid;
        this.critical = critical;
        this.asn1Encodable = asn1Encodable;
    }
    
    public String getOid() {
        return oid;
    }

    public boolean isCritical() {
        return critical;
    }

    public ASN1Encodable getAsn1Encodable() {
        return asn1Encodable;
    }   
}
