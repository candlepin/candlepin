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
package org.fedoraproject.candlepin.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a client certificate.
 * 
 * Used for both entitlement certificates as well as identity certificates. Theoretically
 * could be used for non-X509 certificates as well.
 */
@XmlRootElement(name = "cert")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ClientCertificate extends ClientCertificateMetadata {
    
    private byte[] key;
    private byte[] cert;

    public byte[] getKey() {
        return key;
    }
    public void setKey(byte[] key) {
        this.key = key;
    }
    public byte[] getCert() {
        return cert;
    }
    public void setCert(byte[] cert) {
        this.cert = cert;
    }

}
