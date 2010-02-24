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
 * Represents a client X509 certificate, used to obtain access to some content.
 */
@XmlRootElement(name = "certs")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ClientCertificate implements Persisted {
    
    // This must be Base64 encoded:
    private Bundle bundle;
    private Long id;
    
    /**
     * default ctor
     */
    public ClientCertificate() {
        
    }
    
    /**
     * @param pkcs12Bundle pkcs12 bundle
     */
    public ClientCertificate(Bundle certBundle) {
        this.bundle = certBundle;
    }

    public Bundle getBundle() {
        return bundle;
    }

    public void setBundle(Bundle certBundle) {
        this.bundle = certBundle;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    @Override
    public Long getId() {
        // TODO Auto-generated method stub
        return this.id;
    }

}
