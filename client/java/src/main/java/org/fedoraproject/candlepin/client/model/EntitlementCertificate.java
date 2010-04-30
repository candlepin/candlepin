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
package org.fedoraproject.candlepin.client.model;

import java.io.IOException;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.fedoraproject.candlepin.client.ClientException;
import org.fedoraproject.candlepin.client.PemUtil;

/**
 * Simple Entitlement Certificate Model
 */
@XmlRootElement(name = "cert")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EntitlementCertificate {
    protected String key;
    protected String cert;
    protected BigInteger serial;
    
    public EntitlementCertificate() {
        
    }
    
    public EntitlementCertificate(X509Certificate cert, PrivateKey privateKey) {
        try {
            this.cert = PemUtil.getPemEncoded(cert);
            this.serial = cert.getSerialNumber();
            this.key = PemUtil.getPemEncoded(privateKey);
        } 
        catch (Exception e) {
            throw new ClientException(e);
        }
    }
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public String getCert() {
        return cert;
    }
    public void setCert(String cert) {
        this.cert = cert;
    }
    public BigInteger getSerial() {
        return serial;
    }
    public void setSerial(BigInteger serial) {
        this.serial = serial;
    }
    
    public X509Certificate getX509Cert() {
        return PemUtil.createCert(cert);
    }
    
    public PrivateKey getPrivateKey() {
        return PemUtil.createPrivateKey(key);
    }    
    
    public String getProductName() {
        return PemUtil.getExtensionValue(getX509Cert(), 
            "1.3.6.1.4.1.2312.9.4.1", "Unknown");
    }
    
    public String getStartDate() {
        return PemUtil.getExtensionValue(getX509Cert(), 
            "1.3.6.1.4.1.2312.9.4.6", "Unknown");
    }    
    
    public String getEndDate() {
        return PemUtil.getExtensionValue(getX509Cert(), 
            "1.3.6.1.4.1.2312.9.4.7", "Unknown");
    }        
}
