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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Pool;

/**
 * EntitlementDto
 */
class EntitlementDto {
    private String consumerUuid;
    private Date startDate;
    private Boolean isFree;
    private Integer quantity;
    private List<BigInteger> certificateSerials;
    private String productId;
    private Date endDate;

    public EntitlementDto() {
    }
    
    EntitlementDto(Entitlement e) {
        consumerUuid = e.getConsumer().getUuid();
        startDate = e.getStartDate();
        endDate = e.getEndDate();
        isFree = e.getIsFree();
        quantity = e.getQuantity();
        certificateSerials = certificateSerialNumbers(e.getCertificates());
        productId = e.getPool().getProductId();
    }
    
    public String getConsumerUuid() {
        return consumerUuid;
    }

    public void setConsumerUuid(String consumerUuid) {
        this.consumerUuid = consumerUuid;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startdate) {
        this.startDate = startdate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Boolean getIsFree() {
        return isFree;
    }

    public void setIsFree(Boolean isFree) {
        this.isFree = isFree;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public List<BigInteger> getCertificateSerials() {
        return certificateSerials;
    }

    public void setCertificateSerials(List<BigInteger> certificateSerials) {
        this.certificateSerials = certificateSerials;
    }
    
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Entitlement entitlement(Map<BigInteger, EntitlementCertificate> certs) {        
        Entitlement toReturn = new Entitlement();
        toReturn.setStartDate(startDate);
        toReturn.setIsFree(isFree);
        toReturn.setQuantity(quantity);
        
        for (BigInteger serial : certificateSerials) {
            toReturn.addCertificate(certs.get(serial));
        }
        
        return toReturn;
    }
    
    public Pool poolForEntitlement() {
        Pool toReturn = new Pool();
        toReturn.setProductId(productId);
        toReturn.setStartDate(startDate);
        toReturn.setEndDate(endDate);
        toReturn.setQuantity(new Long(quantity));
        toReturn.setConsumed(new Long(quantity));
        return toReturn;
    }
    
    private List<BigInteger> certificateSerialNumbers(
            Set<EntitlementCertificate> certificates) {
        
        List<BigInteger> toReturn = new LinkedList<BigInteger>();
        for (EntitlementCertificate certificate : certificates) {
            toReturn.add(certificate.getSerial());
        }
        return toReturn;
    }
    
    
}
