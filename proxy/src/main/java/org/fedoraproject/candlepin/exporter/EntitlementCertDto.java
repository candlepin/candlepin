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
package org.fedoraproject.candlepin.exporter;

import java.math.BigInteger;

import org.fedoraproject.candlepin.model.EntitlementCertificate;

/**
 * EntitlementCertDto
 */
public class EntitlementCertDto {

    private BigInteger serialNumber;
    private String certificate;
    private String key;

    EntitlementCertDto() {
    }
    
    EntitlementCertDto(EntitlementCertificate cert) {
        this.serialNumber = cert.getSerial();
        this.certificate = cert.getCert();
        this.key = cert.getKey();
    }

    public BigInteger getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(BigInteger serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
    
    public EntitlementCertificate certificate() {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        toReturn.setSerial(serialNumber);
        toReturn.setCert(certificate);
        toReturn.setKey(key);
        return toReturn;
    }
}
