package org.fedoraproject.candlepin.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a client X509 certificate, used to obtain access to some content.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ClientCertificate {
    
    // This must be Base64 encoded:
    private String pkcs12Bundle;
    
    public ClientCertificate() {
        
    }
    
    public ClientCertificate(String pkcs12Bundle) {
        this.pkcs12Bundle = pkcs12Bundle;
    }

    public String getPkcs12Bundle() {
        return pkcs12Bundle;
    }

    public void setPkcs12Bundle(String pkcs12Bundle) {
        this.pkcs12Bundle = pkcs12Bundle;
    }
}
