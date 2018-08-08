/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import io.swagger.annotations.ApiModel;

import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The ComplianceStatusDTO is a DTO representing the compliance status details output through the
 * API.
 */
@ApiModel(parent = CandlepinDTO.class, description = "DTO representing system purpose compliance status")
public class SystemPurposeComplianceStatusDTO extends CandlepinDTO<SystemPurposeComplianceStatusDTO> {

    protected String status;
    protected Boolean compliant;
    protected Date date;

    protected String nonCompliantRole;
    protected Set<String> nonCompliantAddOns;
    protected String nonCompliantSLA;
    protected String nonCompliantUsage;
    protected Map<String, Set<EntitlementDTO>> compliantRole;
    protected Map<String, Set<EntitlementDTO>> compliantAddOns;
    protected Map<String, Set<EntitlementDTO>> compliantSLA;
    protected Map<String, Set<EntitlementDTO>> compliantUsage;
    protected Map<String, Set<EntitlementDTO>> nonPreferredSLA;
    protected Map<String, Set<EntitlementDTO>> nonPreferredUsage;

    protected Set<String> reasons;


    public SystemPurposeComplianceStatusDTO() {
        // Intentionally left empty
    }

    public SystemPurposeComplianceStatusDTO(SystemPurposeComplianceStatusDTO source) {
        super(source);
    }

    private Map<String, Set<EntitlementDTO>> getMapOfSets(Map<String, Set<EntitlementDTO>> map) {
        Map<String, Set<EntitlementDTO>> result = null;
        if (map != null) {
            result = new HashMap<>();
            for (Map.Entry<String, Set<EntitlementDTO>> entry : map.entrySet()) {
                result.put(entry.getKey(), new SetView<>(entry.getValue()));
            }
        }
        return result;
    }

    public String getStatus() {
        return this.status;
    }

    public SystemPurposeComplianceStatusDTO setStatus(String status) {
        this.status = status;
        return this;
    }

    public Boolean isCompliant() {
        return this.compliant;
    }

    public SystemPurposeComplianceStatusDTO setCompliant(Boolean compliant) {
        this.compliant = compliant;
        return this;
    }

    public Date getDate() {
        return this.date;
    }

    public SystemPurposeComplianceStatusDTO setDate(Date date) {
        this.date = date;
        return this;
    }

    public String getNonCompliantRole() {
        return this.nonCompliantRole;
    }

    public SystemPurposeComplianceStatusDTO setNonCompliantRole(String nonCompliantRole) {
        this.nonCompliantRole = nonCompliantRole;
        return this;
    }

    public Set<String> getNonCompliantAddOns() {
        return this.nonCompliantAddOns != null ? new SetView(this.nonCompliantAddOns) : null;
    }

    public SystemPurposeComplianceStatusDTO setNonCompliantAddOns(Set<String> nonCompliantAddOns) {
        if (nonCompliantAddOns != null) {
            if (this.nonCompliantAddOns == null) {
                this.nonCompliantAddOns = new HashSet<>();
            }

            this.nonCompliantAddOns.clear();
            this.nonCompliantAddOns.addAll(nonCompliantAddOns);
        }
        else {
            this.nonCompliantAddOns = null;
        }

        return this;
    }

    public String getNonCompliantSLA() {
        return this.nonCompliantSLA;
    }

    public SystemPurposeComplianceStatusDTO setNonCompliantSLA(String nonCompliantSLA) {
        this.nonCompliantSLA = nonCompliantSLA;
        return this;
    }

    public String getNonCompliantUsage() {
        return this.nonCompliantUsage;
    }

    public SystemPurposeComplianceStatusDTO setNonCompliantUsage(String nonCompliantUsage) {
        this.nonCompliantUsage = nonCompliantUsage;
        return this;
    }

    public Map<String, Set<EntitlementDTO>> getCompliantRole() {
        return getMapOfSets(compliantRole);
    }

    public SystemPurposeComplianceStatusDTO setCompliantRole(
        Map<String, Set<EntitlementDTO>> compliantRole) {
        if (compliantRole != null) {
            if (this.compliantRole == null) {
                this.compliantRole = new HashMap<>();
            }

            this.compliantRole.clear();
            this.compliantRole.putAll(compliantRole);
        }
        else {
            this.compliantRole = null;
        }

        return this;
    }

    public Map<String, Set<EntitlementDTO>> getCompliantAddOns() {
        return getMapOfSets(compliantAddOns);
    }

    public SystemPurposeComplianceStatusDTO setCompliantAddOns(
        Map<String, Set<EntitlementDTO>> compliantAddOns) {
        if (compliantAddOns != null) {
            if (this.compliantAddOns == null) {
                this.compliantAddOns = new HashMap<>();
            }

            this.compliantAddOns.clear();
            this.compliantAddOns.putAll(compliantAddOns);
        }
        else {
            this.compliantAddOns = null;
        }

        return this;
    }

    public Map<String, Set<EntitlementDTO>> getCompliantSLA() {
        return getMapOfSets(compliantSLA);
    }

    public SystemPurposeComplianceStatusDTO setCompliantSLA(
        Map<String, Set<EntitlementDTO>> compliantSLA) {
        if (compliantSLA != null) {
            if (this.compliantSLA == null) {
                this.compliantSLA = new HashMap<>();
            }

            this.compliantSLA.clear();
            this.compliantSLA.putAll(compliantSLA);
        }
        else {
            this.compliantSLA = null;
        }

        return this;
    }

    public Map<String, Set<EntitlementDTO>> getCompliantUsage() {
        return getMapOfSets(compliantUsage);
    }

    public SystemPurposeComplianceStatusDTO setCompliantUsage(
        Map<String, Set<EntitlementDTO>> compliantUsage) {
        if (compliantUsage != null) {
            if (this.compliantUsage == null) {
                this.compliantUsage = new HashMap<>();
            }

            this.compliantUsage.clear();
            this.compliantUsage.putAll(compliantUsage);
        }
        else {
            this.compliantUsage = null;
        }

        return this;
    }

    public Map<String, Set<EntitlementDTO>> getNonPreferredSLA() {
        return getMapOfSets(nonPreferredSLA);
    }

    public SystemPurposeComplianceStatusDTO setNonPreferredSLA(
        Map<String, Set<EntitlementDTO>> nonPreferredSLA) {
        if (nonPreferredSLA != null) {
            if (this.nonPreferredSLA == null) {
                this.nonPreferredSLA = new HashMap<>();
            }

            this.nonPreferredSLA.clear();
            this.nonPreferredSLA.putAll(nonPreferredSLA);
        }
        else {
            this.nonPreferredSLA = null;
        }

        return this;
    }

    public Map<String, Set<EntitlementDTO>> getNonPreferredUsage() {
        return getMapOfSets(nonPreferredUsage);
    }

    public SystemPurposeComplianceStatusDTO setNonPreferredUsage(
        Map<String, Set<EntitlementDTO>> nonPreferredUsage) {
        if (nonPreferredUsage != null) {
            if (this.nonPreferredUsage == null) {
                this.nonPreferredUsage = new HashMap<>();
            }

            this.nonPreferredUsage.clear();
            this.nonPreferredUsage.putAll(nonPreferredUsage);
        }
        else {
            this.nonPreferredUsage = null;
        }

        return this;
    }

    public Set<String> getReasons() {
        return this.reasons != null ? new SetView(this.reasons) : null;
    }

    public SystemPurposeComplianceStatusDTO setReasons(Collection<String> reasons) {
        if (reasons != null) {
            if (this.reasons == null) {
                this.reasons = new HashSet<>();
            }

            this.reasons.clear();
            this.reasons.addAll(reasons);
        }
        else {
            this.reasons = null;
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

        if (obj instanceof SystemPurposeComplianceStatusDTO) {
            SystemPurposeComplianceStatusDTO that = (SystemPurposeComplianceStatusDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getStatus(), that.getStatus())
                .append(this.isCompliant(), that.isCompliant())
                .append(this.getDate(), that.getDate())
                .append(this.getNonCompliantRole(), that.getNonCompliantRole())
                .append(this.getNonCompliantAddOns(), that.getNonCompliantAddOns())
                .append(this.getNonCompliantSLA(), that.getNonCompliantSLA())
                .append(this.getNonCompliantUsage(), that.getNonCompliantUsage())
                .append(this.getReasons(), that.getReasons());

            boolean equals = builder.isEquals();

            equals = equals && this.compareMapContents(this.getCompliantRole(), that.getCompliantRole());
            equals = equals && this.compareMapContents(this.getCompliantAddOns(), that.getCompliantAddOns());
            equals = equals && this.compareMapContents(this.getCompliantSLA(), that.getCompliantSLA());
            equals = equals && this.compareMapContents(this.getCompliantUsage(), that.getCompliantUsage());
            equals = equals && this.compareMapContents(this.getNonPreferredSLA(), that.getNonPreferredSLA());
            equals = equals && this.compareMapContents(this.getNonPreferredUsage(),
                that.getNonPreferredUsage());

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
    private boolean compareMapContents(Map<String, Set<EntitlementDTO>> map1,
        Map<String, Set<EntitlementDTO>> map2) {

        Comparator<EntitlementDTO> comparator = new Comparator<EntitlementDTO>() {
            public int compare(EntitlementDTO dto1, EntitlementDTO dto2) {
                String id1 = dto1 != null ? dto1.getId() : null;
                String id2 = dto2 != null ? dto2.getId() : null;

                return id1 != null && id1.equals(id2) ? 0 : 1;
            }
        };

        if (map1 == map2) {
            return true;
        }

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

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        Date compliedDate = this.getDate();
        String date = compliedDate != null ? String.format("%1$tF %1$tT%1$tz", compliedDate) : null;

        return String.format("SystemPurposeComplianceStatusDTO [compliant: %b, compiled: %s]", this
            .isCompliant(), date);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int compliantRoleHash = this.buildMapHash(this.getCompliantRole());
        int compliantAddOnsHash = this.buildMapHash(this.getCompliantAddOns());
        int compliantUsageHash = this.buildMapHash(this.getCompliantUsage());
        int compliantSLAHash = this.buildMapHash(this.getCompliantSLA());
        int nonPreferredSLAHash = this.buildMapHash(this.getNonPreferredSLA());
        int nonPreferredUsageHash = this.buildMapHash(this.getNonPreferredUsage());

        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(this.getStatus())
            .append(this.isCompliant())
            .append(this.getDate())
            .append(compliantRoleHash)
            .append(compliantAddOnsHash)
            .append(compliantUsageHash)
            .append(compliantSLAHash)
            .append(nonPreferredSLAHash)
            .append(nonPreferredUsageHash)
            .append(this.getNonCompliantRole())
            .append(this.getNonCompliantAddOns())
            .append(this.getNonCompliantUsage())
            .append(this.getNonCompliantSLA())
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
    public SystemPurposeComplianceStatusDTO clone() {
        SystemPurposeComplianceStatusDTO copy = super.clone();

        Date date = this.getDate();
        copy.setDate(date != null ? (Date) date.clone() : null);

        Map<String, Set<EntitlementDTO>> compliantRoleMap = this.getCompliantRole();
        copy.setCompliantRole(null);
        if (compliantRoleMap != null) {
            copy.setCompliantRole(compliantRoleMap);
        }

        Map<String, Set<EntitlementDTO>> compliantAddOnMap = this.getCompliantAddOns();
        copy.setCompliantAddOns(null);
        if (compliantAddOnMap != null) {
            copy.setCompliantAddOns(compliantAddOnMap);
        }

        Map<String, Set<EntitlementDTO>> compliantUsageMap = this.getCompliantUsage();
        copy.setCompliantUsage(null);
        if (compliantUsageMap != null) {
            copy.setCompliantUsage(compliantUsageMap);
        }

        Map<String, Set<EntitlementDTO>> compliantSLAMap = this.getCompliantSLA();
        copy.setCompliantSLA(null);
        if (compliantSLAMap != null) {
            copy.setCompliantSLA(compliantSLAMap);
        }

        Map<String, Set<EntitlementDTO>> nonPreferredSLAMap = this.getNonPreferredSLA();
        copy.setNonPreferredSLA(null);
        if (nonPreferredSLAMap != null) {
            copy.setNonPreferredSLA(nonPreferredSLAMap);
        }

        Map<String, Set<EntitlementDTO>> nonPreferredUsageMap = this.getNonPreferredUsage();
        copy.setNonPreferredUsage(null);
        if (nonPreferredUsageMap != null) {
            copy.setNonPreferredUsage(nonPreferredUsageMap);
        }

        Set<String> nonCompliantAddOns = this.getNonCompliantAddOns();
        copy.setNonCompliantAddOns(null);
        if (nonCompliantAddOns != null) {
            copy.setNonCompliantAddOns(nonCompliantAddOns);
        }

        Set<String> reasons = this.getReasons();
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
    public SystemPurposeComplianceStatusDTO populate(SystemPurposeComplianceStatusDTO source) {
        super.populate(source);

        if (source != this) {
            this.setStatus(source.getStatus());
            this.setCompliant(source.isCompliant());
            this.setDate(source.getDate());
            this.setCompliantRole(source.getCompliantRole());
            this.setCompliantAddOns(source.getCompliantAddOns());
            this.setCompliantUsage(source.getCompliantUsage());
            this.setCompliantSLA(source.getCompliantSLA());
            this.setNonPreferredSLA(source.getNonPreferredSLA());
            this.setNonPreferredUsage(source.getNonPreferredUsage());
            this.setNonCompliantRole(source.getNonCompliantRole());
            this.setNonCompliantAddOns(source.getNonCompliantAddOns());
            this.setNonCompliantUsage(source.getNonCompliantUsage());
            this.setNonCompliantSLA(source.getNonCompliantSLA());
            this.setReasons(source.getReasons());
        }

        return this;
    }

}
