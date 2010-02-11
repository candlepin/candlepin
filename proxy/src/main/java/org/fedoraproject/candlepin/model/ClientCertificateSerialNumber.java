package org.fedoraproject.candlepin.model;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a client X509 certificate, used to obtain access to some content.
 */
@XmlRootElement(name="certficate_serial_number")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ClientCertificateSerialNumber implements Persisted {

    public long id;
    public String serialNumber;
    
    
    public ClientCertificateSerialNumber() {
        
    }
    
    public ClientCertificateSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }
    
    public String getSerialNumber() {
        return serialNumber;
    }


    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }


    @Override
    public Serializable getId() {
        // TODO Auto-generated method stub
        return id;
    }

}
