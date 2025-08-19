/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.model;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public class ConsumerFeed {

    private String id;
    private String uuid;
    private String name;
    private String typeId;
    private String ownerKey;
    private Date lastCheckin;
    private String guestId;
    private String hypervisorUuid;
    private String hypervisorName;
    private String serviceLevel;
    private String syspurposeRole;
    private String syspurposeUsage;
    private Set<String> syspurposeAddons;
    private Map<String, String> facts;
    private Set<ConsumerFeedInstalledProduct> installedProducts;

    public ConsumerFeed() {
        // Intentionally left empty
    }

    // Do not delete even if it is marked as unused it is used in one of the HQL queries
    public ConsumerFeed(String id, String uuid, String name, String typeId, String ownerKey,
        Date lastCheckin, String serviceLevel, String role) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.typeId = typeId;
        this.ownerKey = ownerKey;
        this.lastCheckin = lastCheckin;
        this.serviceLevel = serviceLevel;
        this.syspurposeRole = role;
    }

    public String getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getTypeId() {
        return typeId;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public Date getLastCheckin() {
        return lastCheckin;
    }

    public String getGuestId() {
        return guestId;
    }

    public String getHypervisorUuid() {
        return hypervisorUuid;
    }

    public String getHypervisorName() {
        return hypervisorName;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public String getSyspurposeRole() {
        return syspurposeRole;
    }

    public String getSyspurposeUsage() {
        return syspurposeUsage;
    }

    public Set<String> getSyspurposeAddons() {
        return syspurposeAddons;
    }

    public Map<String, String> getFacts() {
        return facts;
    }

    public Set<ConsumerFeedInstalledProduct> getInstalledProducts() {
        return installedProducts;
    }

    public ConsumerFeed setId(String id) {
        this.id = id;
        return this;
    }

    public ConsumerFeed setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    public ConsumerFeed setName(String name) {
        this.name = name;
        return this;
    }

    public ConsumerFeed setTypeId(String typeId) {
        this.typeId = typeId;
        return this;
    }

    public ConsumerFeed setOwnerKey(String ownerKey) {
        this.ownerKey = ownerKey;
        return this;
    }

    public ConsumerFeed setLastCheckin(Date lastCheckin) {
        this.lastCheckin = lastCheckin;
        return this;
    }

    public ConsumerFeed setGuestId(String guestId) {
        this.guestId = guestId;
        return this;
    }

    public ConsumerFeed setHypervisorUuid(String hypervisorUuid) {
        this.hypervisorUuid = hypervisorUuid;
        return this;
    }

    public ConsumerFeed setHypervisorName(String hypervisorName) {
        this.hypervisorName = hypervisorName;
        return this;
    }

    public ConsumerFeed setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
        return this;
    }

    public ConsumerFeed setSyspurposeRole(String syspurposeRole) {
        this.syspurposeRole = syspurposeRole;
        return this;
    }

    public ConsumerFeed setSyspurposeUsage(String syspurposeUsage) {
        this.syspurposeUsage = syspurposeUsage;
        return this;
    }

    public ConsumerFeed setSyspurposeAddons(Set<String> syspurposeAddons) {
        this.syspurposeAddons = syspurposeAddons;
        return this;
    }

    public ConsumerFeed setFacts(Map<String, String> facts) {
        this.facts = facts;
        return this;
    }

    public ConsumerFeed setInstalledProducts(Set<ConsumerFeedInstalledProduct> installedProducts) {
        this.installedProducts = installedProducts;
        return this;
    }
}
