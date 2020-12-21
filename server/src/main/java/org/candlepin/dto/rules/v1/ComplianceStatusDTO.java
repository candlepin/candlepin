/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.dto.api.v1.DateRange;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * The ComplianceStatusDTO is a DTO representing the compliance status details
 * output/input for the Rules framework.
 * <tt>
 *   {
 *     "date": "2018-06-06T19:16:17.376Z",
 *     "compliantUntil": "2018-06-06T19:16:17.376Z",
 *     "nonCompliantProducts": [
 *       "string"
 *     ],
 *     "compliantProducts": {},
 *     "partiallyCompliantProducts": {},
 *     "partialStacks": {},
 *     "reasons": [
 *       {
 *         "key": "string",
 *         "message": "string",
 *         "attributes": {}
 *       }
 *     ],
 *     "compliant": false,
 *     "status": "string"
 *   }
 * </tt>
 */
public class ComplianceStatusDTO extends CandlepinDTO<ComplianceStatusDTO> {

    protected String status;
    protected Boolean compliant;

    protected Date date;
    protected Date compliantUntil;

    protected Map<String, Set<EntitlementDTO>> compliantProducts;
    protected Set<String> nonCompliantProducts;
    protected Map<String, Set<EntitlementDTO>> partiallyCompliantProducts;
    protected Map<String, Set<EntitlementDTO>> partialStacks;
    protected Map<String, DateRange> productComplianceDateRanges;

    protected Set<ComplianceReasonDTO> complianceReasons;


    public ComplianceStatusDTO() {
        // Intentionally left empty
    }

    public ComplianceStatusDTO(ComplianceStatusDTO source) {
        this.populate(source);
    }

    public String getStatus() {
        return this.status;
    }

    public ComplianceStatusDTO setStatus(String status) {
        this.status = status;
        return this;
    }

    public Boolean isCompliant() {
        return this.compliant;
    }

    public ComplianceStatusDTO setCompliant(Boolean compliant) {
        this.compliant = compliant;
        return this;
    }

    public Date getDate() {
        return this.date;
    }

    public ComplianceStatusDTO setDate(Date date) {
        this.date = date;
        return this;
    }

    public Date getCompliantUntil() {
        return this.compliantUntil;
    }

    public ComplianceStatusDTO setCompliantUntil(Date compliantUntil) {
        this.compliantUntil = compliantUntil;
        return this;
    }

    public Map<String, Set<EntitlementDTO>> getCompliantProducts() {
        return this.compliantProducts != null ? new MapView<>(this.compliantProducts) : null;
    }

    public ComplianceStatusDTO setCompliantProducts(Map<String, Set<EntitlementDTO>> compliantProducts) {
        if (compliantProducts != null) {
            if (this.compliantProducts == null) {
                this.compliantProducts = new HashMap<>();
            }

            this.compliantProducts.clear();
            this.compliantProducts.putAll(compliantProducts);
        }
        else {
            this.compliantProducts = null;
        }

        return this;
    }
    // addCompliantProduct
    // removeCompliantProduct

    public Set<String> getNonCompliantProducts() {
        return this.nonCompliantProducts != null ? new SetView<>(this.nonCompliantProducts) : null;
    }

    public ComplianceStatusDTO setNonCompliantProducts(Collection<String> nonCompliantProducts) {
        if (nonCompliantProducts != null) {
            if (this.nonCompliantProducts == null) {
                this.nonCompliantProducts = new HashSet<>();
            }

            this.nonCompliantProducts.clear();
            this.nonCompliantProducts.addAll(nonCompliantProducts);
        }
        else {
            this.nonCompliantProducts = null;
        }

        return this;
    }
    // addNonCompliantProduct
    // removeNonCompliantProducts

    public Map<String, Set<EntitlementDTO>> getPartiallyCompliantProducts() {
        return this.partiallyCompliantProducts != null ?
            new MapView<>(this.partiallyCompliantProducts) : null;
    }

    public ComplianceStatusDTO setPartiallyCompliantProducts(Map<String, Set<EntitlementDTO>> entMap) {
        if (entMap != null) {
            if (this.partiallyCompliantProducts == null) {
                this.partiallyCompliantProducts = new HashMap<>();
            }

            this.partiallyCompliantProducts.clear();
            this.partiallyCompliantProducts.putAll(entMap);
        }
        else {
            this.partiallyCompliantProducts = null;
        }

        return this;
    }
    // addPartiallyCompliantProduct
    // removePartiallyCompliantProduct

    public Map<String, Set<EntitlementDTO>> getPartialStacks() {
        return this.partialStacks != null ? new MapView<>(this.partialStacks) : null;
    }

    public ComplianceStatusDTO setPartialStacks(Map<String, Set<EntitlementDTO>> entMap) {
        if (entMap != null) {
            if (this.partialStacks == null) {
                this.partialStacks = new HashMap<>();
            }

            this.partialStacks.clear();
            this.partialStacks.putAll(entMap);
        }
        else {
            this.partialStacks = null;
        }

        return this;
    }
    // addPartialStack
    // removePartialStack

    public Map<String, DateRange> getProductComplianceDateRanges() {
        return this.productComplianceDateRanges != null ?
            new MapView<>(this.productComplianceDateRanges) :
            null;
    }

    public ComplianceStatusDTO setProductComplianceDateRanges(Map<String, DateRange> dateRanges) {
        if (dateRanges != null) {
            if (this.productComplianceDateRanges == null) {
                this.productComplianceDateRanges = new HashMap<>();
            }

            this.productComplianceDateRanges.clear();
            this.productComplianceDateRanges.putAll(dateRanges);
        }
        else {
            this.productComplianceDateRanges = null;
        }

        return this;
    }
    // addProductComplianceDateRange
    // removeProductComplianceDateRange

    public Set<ComplianceReasonDTO> getReasons() {
        return this.complianceReasons != null ? new SetView<>(this.complianceReasons) : null;
    }

    public ComplianceStatusDTO setReasons(Collection<ComplianceReasonDTO> reasons) {
        if (reasons != null) {
            if (this.complianceReasons == null) {
                this.complianceReasons = new HashSet<>();
            }

            this.complianceReasons.clear();
            this.complianceReasons.addAll(reasons);
        }
        else {
            this.complianceReasons = null;
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ComplianceStatusDTO) {
            ComplianceStatusDTO that = (ComplianceStatusDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getStatus(), that.getStatus())
                .append(this.isCompliant(), that.isCompliant())
                .append(this.getDate(), that.getDate())
                .append(this.getCompliantUntil(), that.getCompliantUntil())
                .append(this.getNonCompliantProducts(), that.getNonCompliantProducts())
                .append(this.getProductComplianceDateRanges(), that.getProductComplianceDateRanges())
                .append(this.getReasons(), that.getReasons());

            boolean equals = builder.isEquals();

            equals = equals && this.compareMapHash(this.getCompliantProducts(), that.getCompliantProducts());

            equals = equals && this.compareMapHash(
                this.getPartiallyCompliantProducts(), that.getPartiallyCompliantProducts());

            equals = equals && this.compareMapHash(this.getPartialStacks(), that.getPartialStacks());

            return equals;
        }

        return false;
    }

    /**
     * Utility method for comparing the maps of entitlements
     *
     * @param map1
     * @param map2
     * @return
     *  True if the maps contain mappings to the same sets of entitlements; false otherwise
     */
    private boolean compareMapHash(Map<String, Set<EntitlementDTO>> map1,
        Map<String, Set<EntitlementDTO>> map2) {

        Comparator<EntitlementDTO> comparator = (dto1, dto2) -> {
            String id1 = dto1 != null ? dto1.getId() : null;
            String id2 = dto2 != null ? dto2.getId() : null;

            return id1 != null && id1.equals(id2) ? 0 : 1;
        };

        if (map1 != null && map2 != null) {
            for (Map.Entry<String, Set<EntitlementDTO>> entry : map1.entrySet()) {
                Set<EntitlementDTO> ents1 = entry.getValue();
                Set<EntitlementDTO> ents2 = map2.get(entry.getKey());

                if (ents1 != ents2) {
                    if (ents1 == null || ents2 == null ||
                        !Util.collectionsAreEqual(ents1, ents2, comparator)) {

                        return false;
                    }
                }
            }

            return true;
        }

        return map1 == map2;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        Date until = this.getCompliantUntil();
        String date = until != null ? String.format("%1$tF %1$tT%1$tz", until) : null;

        return String.format("ComplianceStatusDTO [compliant: %b, until: %s]", this.isCompliant(), date);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int compliantProductsHash = this.buildMapHash(this.getCompliantProducts());
        int partiallyCompliantProductsHash = this.buildMapHash(this.getPartiallyCompliantProducts());
        int partialStacksHash = this.buildMapHash(this.getPartialStacks());

        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(this.getStatus())
            .append(this.isCompliant())
            .append(this.getDate())
            .append(this.getCompliantUntil())
            .append(compliantProductsHash)
            .append(this.getNonCompliantProducts())
            .append(partiallyCompliantProductsHash)
            .append(partialStacksHash)
            .append(this.getProductComplianceDateRanges())
            .append(this.getReasons());

        return builder.toHashCode();
    }

    private int buildMapHash(Map<String, Set<EntitlementDTO>> entitlementMap) {
        int hash = 0;

        if (entitlementMap != null) {
            hash = 3;

            for (Map.Entry<String, Set<EntitlementDTO>> entry : entitlementMap.entrySet()) {
                hash = (13 * hash) + entry.getKey().hashCode();

                if (entry.getValue() != null) {
                    for (EntitlementDTO entitlement : entry.getValue()) {
                        hash = (17 * hash) + entitlement.getId() != null ? entitlement.getId().hashCode() : 0;
                    }
                }
            }
        }

        return hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceStatusDTO clone() {
        ComplianceStatusDTO copy = super.clone();

        Date date = this.getDate();
        copy.setDate(date != null ? (Date) date.clone() : null);

        date = this.getCompliantUntil();
        copy.setCompliantUntil(date != null ? (Date) date.clone() : null);

        Map<String, Set<EntitlementDTO>> entMap = this.getCompliantProducts();
        copy.setCompliantProducts(null);
        if (entMap != null) {
            copy.setCompliantProducts(entMap);
        }

        Set<String> nonCompliantProducts = this.getNonCompliantProducts();
        copy.setNonCompliantProducts(null);
        if (nonCompliantProducts != null) {
            copy.setNonCompliantProducts(nonCompliantProducts);
        }

        entMap = this.getPartiallyCompliantProducts();
        copy.setPartiallyCompliantProducts(null);
        if (entMap != null) {
            copy.setPartiallyCompliantProducts(entMap);
        }

        entMap = this.getPartialStacks();
        copy.setPartialStacks(null);
        if (entMap != null) {
            copy.setPartialStacks(entMap);
        }

        Map<String, DateRange> ranges = this.getProductComplianceDateRanges();
        copy.setProductComplianceDateRanges(null);
        if (ranges != null) {
            copy.setProductComplianceDateRanges(ranges);
        }

        Set<ComplianceReasonDTO> reasons = this.getReasons();
        copy.setReasons(null);
        if (reasons != null) {
            copy.setReasons(reasons);
        }

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceStatusDTO populate(ComplianceStatusDTO source) {
        super.populate(source);

        if (source != this) {
            this.setStatus(source.getStatus());
            this.setCompliant(source.isCompliant());
            this.setDate(source.getDate());
            this.setCompliantUntil(source.getCompliantUntil());
            this.setCompliantProducts(source.getCompliantProducts());
            this.setNonCompliantProducts(source.getNonCompliantProducts());
            this.setPartiallyCompliantProducts(source.getPartiallyCompliantProducts());
            this.setPartialStacks(source.getPartialStacks());
            this.setProductComplianceDateRanges(source.getProductComplianceDateRanges());
            this.setReasons(source.getReasons());
        }

        return this;
    }

}
