package org.fedoraproject.candlepin.model;



import java.io.Serializable;

import javax.persistence.Entity;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a client X509 certificate, used to obtain access to some content.
 */
@XmlRootElement(name="clientCertStatus")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ClientCertificateStatus implements Persisted {
    public String serialNumber;
    public String status;
    public ClientCertificate certificate;
    private Long id;
    
    public ClientCertificateStatus() {
     
    }
    
    public ClientCertificateStatus(String serialNumber, String status, ClientCertificate clientCertificate) {
        this.serialNumber = serialNumber;
        this.status = status;
        this.certificate = clientCertificate;
    }
    
    public ClientCertificate getClientCertificate() {
        return certificate;
    }

    public void setClientCertificate(ClientCertificate clientCertificate) {
        this.certificate = clientCertificate;
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
    }

    public void setId(Long id) {
        this.id = id;
    }
    
    @Override
    public Long getId() {
        // TODO Auto-generated method stub
        return this.id;
    };
    
    

}
