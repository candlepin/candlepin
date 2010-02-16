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
@XmlRootElement(name = "clientCertStatus")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ClientCertificateStatus implements Persisted {
    private String serialNumber;
    private String status;
    private ClientCertificate certificate;
    private Long id;
    
    /**
     * default ctor
     */
    public ClientCertificateStatus() {
     
    }
    
    /**
     * creates a new status
     * @param serialNumber certificate serial number
     * @param status status message
     * @param clientCertificate the certificate itself.
     */
    public ClientCertificateStatus(String serialNumber, String status,
            ClientCertificate clientCertificate) {

        this.serialNumber = serialNumber;
        this.status = status;
        this.certificate = clientCertificate;
    }
    
    /**
     * @return returns the client certificate.
     */
    public ClientCertificate getClientCertificate() {
        return certificate;
    }

    /**
     * @param clientCertificate actual client certificate.
     */
    public void setClientCertificate(ClientCertificate clientCertificate) {
        this.certificate = clientCertificate;
    }

  

    /**
     * @return the certificate serial number.
     */
    public String getSerialNumber() {
        return serialNumber;
    }

    /**
     * @param serialNumber certificate serialnumber
     */
    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    /**
     * @return the status message.
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param status status message.
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @param id status id.
     */
    public void setId(Long id) {
        this.id = id;
    }
    
    @Override
    public Long getId() {
        // TODO Auto-generated method stub
        return this.id;
    }
}
