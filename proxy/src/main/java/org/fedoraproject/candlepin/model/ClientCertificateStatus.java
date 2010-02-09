package org.fedoraproject.candlepin.model;



import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a client X509 certificate, used to obtain access to some content.
 */
@XmlRootElement(name="certs")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ClientCertificateStatus {
    public String serialNumber;
    public String status;
    public ClientCertificate clientCertificate;
    
    public ClientCertificateStatus() {
        
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    };
    
    

}
