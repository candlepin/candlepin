/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.TimestampedCandlepinDTO;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Date;

/**
 * Mutable subscription data for version 2 manifests
 */
public class SubscriptionDTO extends TimestampedCandlepinDTO<SubscriptionDTO> {

    public static final long serialVersionUID = 1L;

    private String subscriptionId;
    private Date startDate;
    private Date endDate;
    private String contractNumber;
    private String accountNumber;
    private String productId;

    /**
     * Initializes an empty SubscriptionDTO
     */
    public SubscriptionDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a shallow copy of the source DTO
     *
     * @param source
     *     the DTO to copy
     */
    public SubscriptionDTO(SubscriptionDTO source) {
        super(source);
    }

    /**
     * Retrieves the subscription id
     *
     * @return the subscription id, or null if unset
     */
    public String getSubscriptionId() {
        return this.subscriptionId;
    }

    /**
     * Sets the subscription id
     *
     * @param subscriptionId
     *     the subscription id, or null to clear it
     * @return this DTO
     */
    public SubscriptionDTO setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
        return this;
    }

    /**
     * Retrieves the start date
     *
     * @return the start date, or null if unset
     */
    public Date getStartDate() {
        return this.startDate;
    }

    /**
     * Sets the start date
     *
     * @param startDate
     *     the start date, or null to clear it
     * @return this DTO
     */
    public SubscriptionDTO setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    /**
     * Retrieves the end date
     *
     * @return the end date, or null if unset
     */
    public Date getEndDate() {
        return this.endDate;
    }

    /**
     * Sets the end date
     *
     * @param endDate
     *     the end date, or null to clear it
     * @return this DTO
     */
    public SubscriptionDTO setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    /**
     * Retrieves the contract number
     *
     * @return the contract number, or null if unset
     */
    public String getContractNumber() {
        return this.contractNumber;
    }

    /**
     * Sets the contract number
     *
     * @param contractNumber
     *     the contract number, or null to clear it
     * @return this DTO
     */
    public SubscriptionDTO setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
        return this;
    }

    /**
     * Retrieves the account number
     *
     * @return the account number, or null if unset
     */
    public String getAccountNumber() {
        return this.accountNumber;
    }

    /**
     * Sets the account number
     *
     * @param accountNumber
     *     the account number, or null to clear it
     * @return this DTO
     */
    public SubscriptionDTO setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    /**
     * Retrieves the product id
     *
     * @return the product id, or null if unset
     */
    public String getProductId() {
        return this.productId;
    }

    /**
     * Sets the product id
     *
     * @param productId
     *     the product id, or null to clear it
     * @return this DTO
     */
    public SubscriptionDTO setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof SubscriptionDTO that) || !super.equals(obj)) {
            return false;
        }

        return new EqualsBuilder()
            .append(this.getSubscriptionId(), that.getSubscriptionId())
            .append(this.getStartDate(), that.getStartDate())
            .append(this.getEndDate(), that.getEndDate())
            .append(this.getContractNumber(), that.getContractNumber())
            .append(this.getAccountNumber(), that.getAccountNumber())
            .append(this.getProductId(), that.getProductId())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getSubscriptionId())
            .append(this.getStartDate())
            .append(this.getEndDate())
            .append(this.getContractNumber())
            .append(this.getAccountNumber())
            .append(this.getProductId())
            .toHashCode();
    }

    @Override
    public SubscriptionDTO populate(SubscriptionDTO source) {
        super.populate(source);

        this.setSubscriptionId(source.getSubscriptionId());
        this.setStartDate(source.getStartDate());
        this.setEndDate(source.getEndDate());
        this.setContractNumber(source.getContractNumber());
        this.setAccountNumber(source.getAccountNumber());
        this.setProductId(source.getProductId());

        return this;
    }

    @Override
    public SubscriptionDTO clone() {
        SubscriptionDTO copy = super.clone();
        copy.setStartDate(this.startDate != null ? (Date) this.startDate.clone() : null);
        copy.setEndDate(this.endDate != null ? (Date) this.endDate.clone() : null);
        return copy;
    }
}
