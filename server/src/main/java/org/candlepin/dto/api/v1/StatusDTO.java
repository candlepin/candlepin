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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.util.SetView;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * Status
 */
@Component
@ApiModel(description = "Version and Status information about running Candlepin server")
@XmlRootElement(name = "status")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class StatusDTO extends CandlepinDTO<StatusDTO> {

    /**
     * The current Suspend Mode of Candlepin
     */
    private String mode;
    /**
     * The reason for the last Suspend Mode change
     */
    private String modeReason;

    /**
     * Last time the mode was changed
     */
    private Date modeChangeTime;

    @ApiModelProperty(example = "true")
    private boolean result;

    @ApiModelProperty(example = "0.9.10")
    private String version;

    @ApiModelProperty(example = "5.8")
    private String rulesVersion;

    @ApiModelProperty(example = "1")
    private String release;

    private boolean standalone;

    private Date timeUTC;

    private String rulesSource;

    @ApiModelProperty(example = "[ \"cores\", \"ram\", \"instance_multiplier\" ]")
    private Set<String> capabilities;


    /**
     * Initializes a new StatusDTO instance with null values.
     */
    public StatusDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new StatusDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public StatusDTO(StatusDTO source) {
        super(source);
    }

    public StatusDTO setResult(boolean result) {
        this.result = result;
        return this;
    }

    public boolean getResult() {
        return result;
    }

    public String getVersion() {
        return version;
    }

    public StatusDTO setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getRelease() {
        return release;
    }

    public StatusDTO setRelease(String release) {
        this.release = release;
        return this;
    }

    public boolean getStandalone() {
        return standalone;
    }

    public StatusDTO setStandalone(boolean standalone) {
        this.standalone = standalone;
        return this;
    }

    public Date getTimeUTC() {
        return timeUTC;
    }

    public StatusDTO setTimeUTC(Date timeUTC) {
        this.timeUTC = timeUTC;
        return this;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public StatusDTO setRulesVersion(String rulesVersion) {
        this.rulesVersion = rulesVersion;
        return this;
    }

    /**
     * @return the rulesSource
     */
    public String getRulesSource() {
        return rulesSource;
    }

    /**
     * @param rulesSource the rulesSource to set
     *
     * @return
     *  A reference to this DTO
     */
    public StatusDTO setRulesSource(String rulesSource) {
        this.rulesSource = rulesSource;
        return this;
    }

    public Set<String> getManagerCapabilities() {
        return this.capabilities != null ? new SetView(this.capabilities) : null;
    }

    /**
     * Sets or clears the available Candlepin/manager capabilities. If the given capabilities are
     * null, any set capabilities will be cleared.
     *
     * @param capabilities
     *  The capabilities to set, or null to clear any existing capabilities
     *
     * @return
     *  A reference to this DTO
     */
    public StatusDTO setManagerCapabilities(Collection<String> capabilities) {
        if (capabilities != null) {
            if (this.capabilities == null) {
                this.capabilities = new HashSet<>();
            }

            this.capabilities.clear();
            this.capabilities.addAll(capabilities);
        }
        else {
            this.capabilities = null;
        }

        return this;
    }

    /**
     * Adds the specified capability to the collection of available manager capabilities. If
     * the capability has already been added to this DTO, this method returns false.
     *
     * @param capability
     *  The capability to add to the available manager capabilities
     *
     * @throws IllegalArgumentException
     *  if capability is null
     *
     * @return
     *  true if the capability was added successfully; false otherwise
     */
    public boolean addManagerCapability(String capability) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is null");
        }

        if (this.capabilities == null) {
            this.capabilities = new HashSet<>();
        }

        return this.capabilities.add(capability);
    }

    /**
     * Removes the specified capability from the collection of available manager capabilities. If
     * the capability has not been added to this DTO, this method returns false.
     *
     * @param capability
     *  The capability to remove from the available manager capabilities
     *
     * @throws IllegalArgumentException
     *  if capability is null
     *
     * @return
     *  true if the capability was removed successfully; false otherwise
     */
    public boolean removeManagerCapability(String capability) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is null");
        }

        return this.capabilities != null ? this.capabilities.remove(capability) : false;
    }

    public String getMode() {
        return mode;
    }

    public StatusDTO setMode(String mode) {
        this.mode = mode;
        return this;
    }

    public String getModeReason() {
        return modeReason;
    }

    public StatusDTO setModeReason(String reason) {
        this.modeReason = reason;
        return this;
    }

    public Date getModeChangeTime() {
        return modeChangeTime;
    }

    public StatusDTO setModeChangeTime(Date time) {
        this.modeChangeTime = time;
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

        if (obj instanceof StatusDTO) {
            StatusDTO that = (StatusDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getMode(), that.getMode())
                .append(this.getModeReason(), that.getModeReason())
                .append(this.getModeChangeTime(), that.getModeChangeTime())
                .append(this.getResult(), that.getResult())
                .append(this.getVersion(), that.getVersion())
                .append(this.getRulesVersion(), that.getRulesVersion())
                .append(this.getRulesSource(), that.getRulesSource())
                .append(this.getRelease(), that.getRelease())
                .append(this.getStandalone(), that.getStandalone())
                .append(this.getTimeUTC(), that.getTimeUTC())
                .append(this.getManagerCapabilities(), that.getManagerCapabilities());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(this.getMode())
            .append(this.getModeReason())
            .append(this.getModeChangeTime())
            .append(this.getResult())
            .append(this.getVersion())
            .append(this.getRulesVersion())
            .append(this.getRulesSource())
            .append(this.getRelease())
            .append(this.getStandalone())
            .append(this.getTimeUTC())
            .append(this.getManagerCapabilities());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StatusDTO clone() {
        StatusDTO copy = super.clone();

        Date modeChangeTime = this.getModeChangeTime();
        copy.setModeChangeTime(modeChangeTime != null ? (Date) modeChangeTime.clone() : null);

        Date timeUTC = this.getTimeUTC();
        copy.setTimeUTC(timeUTC != null ? (Date) timeUTC.clone() : null);

        Set<String> capabilities = this.getManagerCapabilities();
        copy.setManagerCapabilities(null);
        copy.setManagerCapabilities(capabilities);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StatusDTO populate(StatusDTO source) {
        super.populate(source);

        this.setMode(source.getMode());
        this.setModeReason(source.getModeReason());
        this.setModeChangeTime(source.getModeChangeTime());
        this.setResult(source.getResult());
        this.setVersion(source.getVersion());
        this.setRulesVersion(source.getRulesVersion());
        this.setRulesSource(source.getRulesSource());
        this.setRelease(source.getRelease());
        this.setStandalone(source.getStandalone());
        this.setTimeUTC(source.getTimeUTC());
        this.setManagerCapabilities(source.getManagerCapabilities());

        return this;
    }

}
