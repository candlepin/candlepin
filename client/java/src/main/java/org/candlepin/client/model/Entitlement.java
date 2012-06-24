/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.client.model;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonIgnore;

/**
 * Simple entitlement model
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Entitlement extends TimeStampedEntity {
    private Long id;
    private Pool pool;
    private Date startDate;
    private String productId;
    private Set<EntitlementCertificate> certificates =
        new HashSet<EntitlementCertificate>();
    private Boolean free = Boolean.FALSE;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Set<EntitlementCertificate> getCertificates() {
        return certificates;
    }

    public void setCertificates(Set<EntitlementCertificate> certificates) {
        this.certificates = certificates;
    }

    public Boolean isFree() {
        return free;
    }

    public void setFree(Boolean isFree) {
        this.free = isFree;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    @JsonIgnore
    public void setIsFree(boolean bool) {
    }

    private EntitlementCertificate entitlementCertificate;

    @JsonIgnore
    public EntitlementCertificate getEntitlementCertificate() {
        return entitlementCertificate;
    }

    @JsonIgnore
    public void setEntitlementCertificate(EntitlementCertificate entitlement) {
        this.entitlementCertificate = entitlement;
    }

}
