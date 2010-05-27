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

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * AbstractCertificate
 */
@MappedSuperclass
@XmlType(name = "Certificate")
public abstract class AbstractCertificate extends AbstractHibernateObject {

    @Column(nullable = false)
    private byte[] key;

    @Column(nullable = false)
    private byte[] cert;
    
    @XmlTransient
    public void setKey(byte[] key) {
        this.key = key;
    }

    @XmlTransient
    public byte[] getKey() {
        return key;
    }
    
    @XmlElement(name = "key")
    public String getKeyAsString() {
        return new String(key);
    }
    
    public void setKeyAsString(String key) {
        this.key = key.getBytes();        
    }    

    @XmlTransient
    public void setCert(byte[] cert) {
        this.cert = cert;
    }

    @XmlTransient
    public byte[] getCert() {
        return cert;
    }
    
    @XmlElement(name = "cert")
    public String getCertAsString() {
        return new String(cert);
    }
    
    public void setCertAsString(String cert) {
        this.cert = cert.getBytes();        
    }
    
}
