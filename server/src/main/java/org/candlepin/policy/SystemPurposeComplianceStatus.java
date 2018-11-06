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
package org.candlepin.policy;

import org.candlepin.model.Entitlement;

import org.apache.commons.lang3.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ComplianceStatus
 *
 * Represents the system purpose compliance status for a given consumer. Carries information
 * about which roles, SLA, usage and add-ons are fully entitled, not entitled, or partially entitled.
 */
public class SystemPurposeComplianceStatus {
    public static final String GREEN = "valid";
    public static final String YELLOW = "partial";
    public static final String RED = "invalid";

    // Date this compliance was set on
    private Date date;
    private String nonCompliantRole;
    private Set<String> nonCompliantAddOns;
    private String nonCompliantSLA;
    private String nonCompliantUsage;
    private Map<String, Set<Entitlement>> compliantRole;
    private Map<String, Set<Entitlement>> compliantAddOns;
    private Map<String, Set<Entitlement>> compliantSLA;
    private Map<String, Set<Entitlement>> compliantUsage;
    private Map<String, Set<Entitlement>> nonPreferredSLA;
    private Map<String, Set<Entitlement>> nonPreferredUsage;
    private Set<String> reasons;
    private I18n i18n;

    public SystemPurposeComplianceStatus(I18n i18n) {
        this.compliantRole = new HashMap<>();
        this.nonCompliantAddOns = new HashSet<>();
        this.compliantAddOns = new HashMap<>();
        this.nonPreferredSLA = new HashMap<>();
        this.compliantSLA = new HashMap<>();
        this.nonPreferredUsage = new HashMap<>();
        this.compliantUsage = new HashMap<>();
        this.reasons = new HashSet<>();
        this.i18n = i18n;
        this.date = new Date();
    }

    public void setDate(Date date) {
        this.date = date;
    }

    /**
     *
     * @return Date this compliance status was checked for.
     */
    public Date getDate() {
        return date;
    }

    /**
     * @return the role if it is non-compliant, null otherwise
     */
    public String getNonCompliantRole() {
        return nonCompliantRole;
    }

    /**
     * @param nonCompliantRole the role if the consumer is not role compliant, null otherwise
     */
    public void setNonCompliantRole(String nonCompliantRole) {
        this.nonCompliantRole = nonCompliantRole;
        this.addReason("unsatisfied role: {0}", nonCompliantRole);
    }

    /**
     * @return entitlements of the role if the consumer is role compliant
     */
    public Map<String, Set<Entitlement>> getCompliantRole() {
        return compliantRole;
    }

    /**
     *
     * @param role the role that is compliant
     * @param compliantRoleEnt the entitlement that provides the role
     */
    public void addCompliantRole(String role, Entitlement compliantRoleEnt) {
        if (!compliantRole.containsKey(role)) {
            compliantRole.put(role, new HashSet<>());
        }

        compliantRole.get(role).add(compliantRoleEnt);
    }

    /**
     * @return the set of add ons that are not compliant
     */
    public Set<String> getNonCompliantAddOns() {
        return nonCompliantAddOns;
    }

    /**
     *
     * @param nonCompliantAddOn
     */
    public void addNonCompliantAddOn(String nonCompliantAddOn) {
        this.nonCompliantAddOns.add(nonCompliantAddOn);
        this.addReason("unsatisfied add on: {0}", nonCompliantAddOn);
    }

    /**
     *
     * @return the compliant add ons ( if there are any ) and entitlement that provide the add ons
     */
    public Map<String, Set<Entitlement>> getCompliantAddOns() {
        return compliantAddOns;
    }

    /**
     * @param addOn the add on that is compliant
     * @param compliantAddOnEntitlement the entitlement that provides the add on
     */
    public void addCompliantAddOn(String addOn, Entitlement compliantAddOnEntitlement) {
        if (!compliantAddOns.containsKey(addOn)) {
            compliantAddOns.put(addOn, new HashSet<>());
        }

        compliantAddOns.get(addOn).add(compliantAddOnEntitlement);
    }

    /**
     * @return non compliant SLA and the entitlements that do not provide the SLA
     */
    public Map<String, Set<Entitlement>> getNonPreferredSLA() {
        return nonPreferredSLA;
    }

    /**
     *
     * @param expectedSla the sla that is non compliant
     * @param actualSla the sla that is provided by the pool
     * @param nonCompliantSLAEntitlement the entitlements that are not sla compliant
     */
    public void addNonPreferredSLA(String expectedSla, String actualSla,
        Entitlement nonCompliantSLAEntitlement) {

        if (!nonPreferredSLA.containsKey(actualSla)) {
            nonPreferredSLA.put(actualSla, new HashSet<>());
        }

        nonPreferredSLA.get(actualSla).add(nonCompliantSLAEntitlement);

        this.addReason("expected sla is {0} but pool {1} with product {2} provides SLA: {3}",
            expectedSla, nonCompliantSLAEntitlement.getPool().getId(),
            nonCompliantSLAEntitlement.getPool().getProductId(), actualSla);
    }

    /**
     *
     * @return compliant SLA and the entitlements that provide the SLA
     */
    public Map<String, Set<Entitlement>> getCompliantSLA() {
        return compliantSLA;
    }

    /**
     *
     * @param sla the sla that is compliant
     * @param compliantSLAEntitlement the entitlement that provides the sla
     */
    public void addCompliantSLA(String sla, Entitlement compliantSLAEntitlement) {
        if (!compliantSLA.containsKey(sla)) {
            compliantSLA.put(sla, new HashSet<>());
        }

        compliantSLA.get(sla).add(compliantSLAEntitlement);
    }

    /**
     *
     * @param sla the sla that is non compliant
     */
    public void setNonCompliantSLA(String sla) {
        nonCompliantSLA = sla;
        this.addReason("unsatisfied sla: {0}", nonCompliantSLA);
    }

    public String getNonCompliantSLA() {
        return nonCompliantSLA;
    }

    /**
     *
     * @return the non compliant usage and the entitlement that do not provide the usage
     */
    public Map<String, Set<Entitlement>> getNonPreferredUsage() {
        return nonPreferredUsage;
    }

    /**
     *
     * @param expectedUsage the usage that is non compliant
     * @param actualUsage the usage that is provided by the pool
     * @param nonCompliantUsageEntitlement the entitlement that is not compliant
     */
    public void addNonPreferredUsage(String expectedUsage, String actualUsage,
        Entitlement nonCompliantUsageEntitlement) {

        if (!nonPreferredUsage.containsKey(actualUsage)) {
            nonPreferredUsage.put(actualUsage, new HashSet<>());
        }

        nonPreferredUsage.get(actualUsage).add(nonCompliantUsageEntitlement);
        this.addReason("expected usage is {0} but pool {1} with product {2} provides Usage: {3}",
            expectedUsage, nonCompliantUsageEntitlement.getPool().getId(),
            nonCompliantUsageEntitlement.getPool().getProductId(), actualUsage);
    }

    /**
     *
     * @param usage the usage that is non compliant
     */
    public void setNonCompliantUsage(String usage) {
        nonCompliantUsage = usage;
        this.addReason("unsatisfied usage: {0}", nonCompliantUsage);
    }

    public String getNonCompliantUsage() {
        return nonCompliantUsage;
    }

    /**
     *
     * @return the usage that is compliant and the entitlement that provide it
     */
    public Map<String, Set<Entitlement>> getCompliantUsage() {
        return compliantUsage;
    }

    public void addCompliantUsage(String usage, Entitlement compliantUsageEntitlement) {
        if (!compliantUsage.containsKey(usage)) {
            compliantUsage.put(usage, new HashSet<>());
        }

        compliantUsage.get(usage).add(compliantUsageEntitlement);
    }

    public boolean isCompliant() {
        return reasons.isEmpty();
    }

    /*
     * Valid :
     * * no system purpose is specified
     * * SLA is specified, there is a single SLA provided by the consumer's entitlements and its the
     * one specified by the consumer
     * * role is specified and at least one entitlement provides the preferred role
     * * add ons are specified, and all of them are provided by the pools
     * * usage is specified, there is a single usage provided by the consumer's entitlements and its the
     * one specified by the consumer
     *
     * Partial:
     * * SLA is specified, entitlements have mixed SLAs, with one of them matches the preference
     * Add ons are specified, and atleast one of them but not all of them are provided by the pools
     * usage is specified, entitlements have mixed usages, with one of them matches the preference
     *
     * Invalid:
     * * SLA is specified, and entitlements provide a single SLA that does not match preference
     * * SLA is specified, no entitlements provide that SLA preference
     * * Role is specified, and not even one entitlement provides the preferred role
     * * Add ons are specified, and none of the add ons are provided are provided by the pools
     * * usage is specified, and entitlements provide a single usage that does not match preference
     * * usage is specified, no entitlements provide the usage preference
     *
     */
    public String getStatus() {

        if (isCompliant()) {
            return GREEN;
        }

        if (StringUtils.isNotEmpty(nonCompliantRole) ||
            // even if there one add on, should be yellow
            (!nonCompliantAddOns.isEmpty() && compliantAddOns.isEmpty()) ||
            StringUtils.isNotEmpty(nonCompliantSLA) ||
            StringUtils.isNotEmpty(nonCompliantUsage)) {
            return RED;
        }

        return YELLOW;
    }

    public Set<String> getReasons() {
        return reasons;
    }

    private void addReason(String reason, String ... principles) {
        if (reasons == null) {
            reasons = new HashSet<>();
        }

        this.reasons.add(i18n.tr(reason, principles));
    }
}
