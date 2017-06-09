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
package org.candlepin.model;

import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.resteasy.InfoProperty;
import org.candlepin.service.ContentAccessCertServiceAdapter;

import com.fasterxml.jackson.annotation.JsonFilter;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import io.swagger.annotations.ApiModelProperty;



/**
 * Represents the owner of entitlements. This is akin to an organization,
 * whereas a User is an individual account within that organization.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = Owner.DB_TABLE)
@JsonFilter("OwnerFilter")
public class Owner extends AbstractHibernateObject implements Serializable,
    Linkable, Owned, Named, Eventful {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_owner";
    private static final long serialVersionUID = -7059065874812188165L;

    @OneToOne
    @JoinColumn(name = "parent_owner", nullable = true)
    private Owner parentOwner;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    @ApiModelProperty(readOnly = true)
    private String id;

    @Column(name = "account", nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    private String key;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String displayName;

    @Column(nullable = true)
    @Size(max = 255)
    private String contentPrefix;

    @OneToMany(mappedBy = "owner", targetEntity = Consumer.class)
    private Set<Consumer> consumers;

    @OneToMany(mappedBy = "owner", targetEntity = ActivationKey.class)
    private Set<ActivationKey> activationKeys;

    @OneToMany(mappedBy = "owner", targetEntity = Environment.class)
    private Set<Environment> environments;

    @Column(nullable = true)
    @Size(max = 255)
    private String defaultServiceLevel;

    // EntitlementPool is the owning side of this relationship.
    @OneToMany(mappedBy = "owner", targetEntity = Pool.class)
    private Set<Pool> pools;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "upstream_id")
    private UpstreamConsumer upstreamConsumer;

    @Column(nullable = true)
    @Size(max = 32)
    private String logLevel;

    /**
     * When set, autobind will be disabled no matter if it is set
     * on the Consumer or not.
     *
     * NOTE: Need to allow null values here so that we can check
     *       it on Owner update.
     */
    @Column(name = "autobind_disabled")
    private Boolean autobindDisabled;

    /**
     * Determines the behavior of the content access.
     */
    @Column(name = "content_access_mode")
    private String contentAccessMode;

    /**
     * Determines the allowable modes of the content access.
     */
    @Column(name = "content_access_mode_list")
    private String contentAccessModeList;

    /**
     * Default constructor
     */
    public Owner() {
        this.consumers = new HashSet<Consumer>();
        this.pools = new HashSet<Pool>();
        this.environments = new HashSet<Environment>();
    }

    /**
     * Constructor with required parameters.
     *
     * @param key Owner's unique identifier
     * @param displayName Owner's name - suitable for UI
     */
    public Owner(String key, String displayName) {
        this();

        this.key = key;
        this.displayName = displayName;
    }

    /**
     * Creates an Owner with only a name
     *
     * @param name to be used for both the display name and the key
     */
    public Owner(String name) {
        this(name, name);
    }

    /**
     * @return the id
     */
    @Override
    @HateoasInclude
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    @InfoProperty("key")
    @HateoasInclude
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    /**
     * @return the name
     */
    @InfoProperty("displayName")
    @HateoasInclude
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * @param displayName the name to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the content prefix
     */
    public String getContentPrefix() {
        return this.contentPrefix;
    }

    /**
     * @param contentPrefix the prefix to set
     */
    public void setContentPrefix(String contentPrefix) {
        this.contentPrefix = contentPrefix;
    }

    /**
     * @return the consumers
     */
    @XmlTransient
    public Set<Consumer> getConsumers() {
        return consumers;
    }

    /**
     * @param consumers the consumers to set
     */
    public void setConsumers(Set<Consumer> consumers) {
        this.consumers = consumers;
    }

    /**
     * @return the entitlementPools
     */
    @XmlTransient
    public Set<Pool> getPools() {
        return pools;
    }

    /**
     * @param entitlementPools the entitlementPools to set
     */
    public void setPools(Set<Pool> entitlementPools) {
        this.pools = entitlementPools;
    }

    /**
     * Add a consumer to this owner
     *
     * @param c consumer for this owner.
     */
    public void addConsumer(Consumer c) {
        c.setOwner(this);
        this.consumers.add(c);

    }

    /**
     * add owner to the pool, and reference to the pool.
     *
     * @param pool EntitlementPool for this owner.
     */
    public void addEntitlementPool(Pool pool) {
        pool.setOwner(this);
        if (this.pools == null) {
            this.pools = new HashSet<Pool>();
        }
        this.pools.add(pool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Owner [id: " + getId() + ", key: " + getKey() + "]";
    }

    // Generated by Netbeans
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Owner other = (Owner) obj;
        if (this.id != other.id &&
            (this.id == null || !this.id.equals(other.id))) {
            return false;
        }
        if ((this.key == null) ? (other.key != null) : !this.key
            .equals(other.key)) {
            return false;
        }
        if ((this.displayName == null) ? (other.displayName != null) :
            !this.displayName.equals(other.displayName)) {
            return false;
        }
        if ((this.contentPrefix == null) ? (other.contentPrefix != null) :
            !this.contentPrefix.equals(other.contentPrefix)) {
            return false;
        }
        if ((this.autobindDisabled == null) ? (other.autobindDisabled != null) :
            !this.autobindDisabled.equals(other.autobindDisabled)) {
            return false;
        }
        if ((this.contentAccessMode == null) ? (other.contentAccessMode != null) :
            !this.contentAccessMode.equals(other.contentAccessMode)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return 89 * 3 + (this.id != null ? this.id.hashCode() : 0);
    }

    /**
     * @param upstream the upstream consumer to set
     */
    public void setUpstreamConsumer(UpstreamConsumer upstream) {
        this.upstreamConsumer = upstream;
        if (upstream != null) {
            upstream.setOwnerId(id);
        }
    }

    /**
     * @return the upstreamUuid
     */
    @XmlTransient
    public String getUpstreamUuid() {
        if (upstreamConsumer == null) {
            return null;
        }

        return upstreamConsumer.getUuid();
    }

    /**
     * @return the
     */
    public UpstreamConsumer getUpstreamConsumer() {
        return upstreamConsumer;
    }

    @HateoasInclude
    public String getHref() {
        return "/owners/" + getKey();
    }

    public void setHref(String href) {
        /*
         * No-op, here to aid with updating objects which have nested objects
         * that were originally sent down to the client in HATEOAS form.
         */
    }

    public Owner getParentOwner() {
        return parentOwner;
    }

    public void setParentOwner(Owner parentOwner) {
        this.parentOwner = parentOwner;
    }

    /**
     * Kind of crazy - an owner owns itself.  This is so that the OwnerPermissions
     * will work properly when Owner is the target.
     *
     * @return this
     */
    @XmlTransient
    @Override
    public Owner getOwner() {
        return this;
    }

    /**
     * @return the activationKeys
     */
    @XmlTransient
    public Set<ActivationKey> getActivationKeys() {
        return activationKeys;
    }

    /**
     * @param activationKeys the activationKeys to set
     */
    public void setActivationKeys(Set<ActivationKey> activationKeys) {
        this.activationKeys = activationKeys;
    }

    @XmlTransient
    public Set<Environment> getEnvironments() {
        return environments;
    }

    public void setEnvironments(Set<Environment> environments) {
        this.environments = environments;
    }

    public String getDefaultServiceLevel() {
        return defaultServiceLevel;
    }

    public void setDefaultServiceLevel(String defaultServiceLevel) {
        this.defaultServiceLevel = defaultServiceLevel;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    @XmlTransient
    public String getName() {
        return getDisplayName();
    }

    /**
     * Utility method that checks null case for
     * autobind setting since the getter can return null. If autobindDisabled is null,
     * it is considered enabled.
     *
     * @return true if autobind is disabled, false otherwise.
     */
    @XmlTransient
    public boolean autobindDisabled() {
        return getAutobindDisabled() == null ? false : getAutobindDisabled();
    }

    /**
     * Returns the true value of the autobindDisabled setting.
     *
     * @return True if autobind is disabled for this owner, False or null otherwise.
     *         A value of null means that it is unset, and considered in code as False.
     */
    public Boolean getAutobindDisabled() {
        return autobindDisabled;
    }

    public void setAutobindDisabled(Boolean autobindDisabled) {
        this.autobindDisabled = autobindDisabled;
    }

    /**
     * Utility method that defaults to 'entitlement'.
     *  This covers legacy owners
     *
     * @return access mode.
     */
    @XmlTransient
    public String contentAccessMode() {
        return StringUtils.isEmpty(getContentAccessMode()) ?
            ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE : getContentAccessMode();
    }

    /**
     * Returns the value of the contentAccessMode setting.
     *
     * @return String the value
     */
    public String getContentAccessMode() {
        return contentAccessMode;
    }

    public void setContentAccessMode(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;
    }

    /**
     * Returns the value of the contentAccessModeList setting.
     *
     * @return String the value
     */
    public String getContentAccessModeList() {
        String[] modeList = contentAccessModeList == null ? new String[0] : contentAccessModeList.split(",");
        String ammend = "";
        if (!ArrayUtils.contains(modeList, ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE)) {
            ammend = modeList.length > 0 ? "," : "";
            ammend += ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE;
        }
        return modeList.length > 0 ? this.contentAccessModeList + ammend : ammend;
    }

    public void setContentAccessModeList(String contentAccessModeList) {
        this.contentAccessModeList = !StringUtils.isEmpty(contentAccessModeList) ?
            contentAccessModeList :
            null;
    }

    @XmlTransient
    public boolean isAllowedContentAccessMode(String mode) {
        if (StringUtils.isEmpty(mode)) {
            return true;
        }

        if (mode.equals(ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE)) {
            return true;
        }

        if (StringUtils.isEmpty(contentAccessModeList)) {
            return false;
        }

        String[] list = contentAccessModeList.split(",");
        return ArrayUtils.contains(list, mode);
    }

}
