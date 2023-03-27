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
 * about which roles, SLA, usage and add-ons are entitled or not entitled, or if none are specified.
 */
public class SystemPurposeComplianceStatus {
    public static final String MATCHED = "matched";
    public static final String MISMATCHED = "mismatched";
    public static final String NOT_SPECIFIED = "not specified";
    public static final String DISABLED = "disabled";

    // Date this compliance was set on
    private Date date;
    private String nonCompliantRole;
    private Set<String> nonCompliantAddOns;
    private String nonCompliantSLA;
    private String nonCompliantUsage;
    private String nonCompliantServiceType;
    private Map<String, Set<Entitlement>> compliantRole;
    private Map<String, Set<Entitlement>> compliantAddOns;
    private Map<String, Set<Entitlement>> compliantSLA;
    private Map<String, Set<Entitlement>> compliantUsage;
    private Map<String, Set<Entitlement>> compliantServiceType;
    private Set<String> reasons;
    private I18n i18n;
    private boolean disabled = false;

    public SystemPurposeComplianceStatus(I18n i18n) {
        this.compliantRole = new HashMap<>();
        this.nonCompliantAddOns = new HashSet<>();
        this.compliantAddOns = new HashMap<>();
        this.compliantSLA = new HashMap<>();
        this.compliantUsage = new HashMap<>();
        this.compliantServiceType = new HashMap<>();
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
        this.addReason(
            I18n.marktr("The requested role \"{0}\" is not provided " +
            "by a currently consumed subscription."),
            nonCompliantRole
        );
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
        this.addReason(
            I18n.marktr("The requested add-on \"{0}\" is not provided " +
            "by a currently consumed subscription."),
            nonCompliantAddOn
        );
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
        this.addReason(
            I18n.marktr("The service level preference \"{0}\" is not provided " +
            "by a currently consumed subscription."),
            nonCompliantSLA
        );
    }

    public String getNonCompliantSLA() {
        return nonCompliantSLA;
    }

    /**
     *
     * @param usage the usage that is non compliant
     */
    public void setNonCompliantUsage(String usage) {
        nonCompliantUsage = usage;
        this.addReason(
            I18n.marktr("The requested usage preference \"{0}\" is not provided " +
            "by a currently consumed subscription."),
            nonCompliantUsage
        );
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

    /**
     *
     * @param serviceType the usage that is non compliant
     */
    public void setNonCompliantServiceType(String serviceType) {
        nonCompliantServiceType = serviceType;
        this.addReason(
            I18n.marktr("The requested service type preference \"{0}\" is not provided " +
            "by a currently consumed subscription."),
            nonCompliantServiceType
        );
    }

    public String getNonCompliantServiceType() {
        return nonCompliantServiceType;
    }

    /**
     *
     * @return the service type that is compliant and the entitlement that provide it
     */
    public Map<String, Set<Entitlement>> getCompliantServiceType() {
        return compliantServiceType;
    }

    public void addCompliantServiceType(String serviceType, Entitlement compliantServiceEntitlement) {
        if (!compliantServiceType.containsKey(serviceType)) {
            compliantServiceType.put(serviceType, new HashSet<>());
        }

        compliantServiceType.get(serviceType).add(compliantServiceEntitlement);
    }

    /*
     * The status is 'Matched' if at least one syspurpose attribute (SLA, role, addons, usage) is specified,
     * and all of those that are specified are satisfied by the consumer's entitlements.
     *
     * The status is 'Mismatched' if at least one syspurpose attribute (SLA, role, addons, usage)
     * is specified, and from those specified, at least one is not satisfied by the consumer's entitlements.
     * (This includes the scenario of multiple addons being specified, where only some but not all of them
     * are satisfied)
     *
     * The status is 'Not Specified' when NONE of the attributes were specified by the consumer at all.
     */
    public String getStatus() {

        if (isDisabled()) {
            return DISABLED;
        }
        if (isCompliant()) {
            if (isNotSpecified()) {
                return NOT_SPECIFIED;
            }
            return MATCHED;
        }

        return MISMATCHED;
    }

    private boolean isNotSpecified() {
        return StringUtils.isEmpty(nonCompliantRole) &&
                nonCompliantAddOns.isEmpty() &&
                StringUtils.isEmpty(nonCompliantSLA) &&
                StringUtils.isEmpty(nonCompliantUsage) &&
                compliantRole.isEmpty() &&
                compliantAddOns.isEmpty() &&
                compliantSLA.isEmpty() &&
                compliantUsage.isEmpty() &&
                StringUtils.isEmpty(nonCompliantServiceType) &&
                compliantServiceType.isEmpty();
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

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isDisabled() {
        return this.disabled;
    }
}
