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

import org.candlepin.model.Rules.RulesSourceEnum;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Status
 */
@ApiModel(description = "Version and Status information about running Candlepin server")
@XmlRootElement(name = "status")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Status {

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

    @ApiModelProperty(example = "[ \"cores\", \"ram\", \"instance_multiplier\" ]")
    private String[] managerCapabilities = {"cores", "ram", "instance_multiplier",
        "derived_product", "cert_v3", "guest_limit", "vcpu", "hypervisors_async",
        "storage_band", "remove_by_pool_id", "batch_bind"};

    private RulesSourceEnum rulesSource;


    /**
     * default ctor
     */
    public Status() {

    }

    public Status(Boolean result, String version, String release, Boolean standalone,
        String rulesVersion, Rules.RulesSourceEnum rulesSource) {
        this.result = result;
        this.version = version;
        this.release = release;
        this.standalone = standalone;
        this.timeUTC = new Date();
        this.rulesVersion = rulesVersion;
        this.setRulesSource(rulesSource);
    }

    public boolean getResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRelease() {
        return release;
    }

    public void setRelease(String release) {
        this.release = release;
    }

    public boolean getStandalone() {
        return standalone;
    }

    public void setStandalone(boolean standalone) {
        this.standalone = standalone;
    }

    public Date getTimeUTC() {
        return timeUTC;
    }

    public void setTimeUTC(Date timeUTC) {
        this.timeUTC = timeUTC;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public void setRulesVersion(String rulesVersion) {
        this.rulesVersion = rulesVersion;
    }

    /**
     * @return the rulesSource
     */
    public RulesSourceEnum getRulesSource() {
        return rulesSource;
    }

    /**
     * @param rulesSource the rulesSource to set
     */
    public void setRulesSource(Rules.RulesSourceEnum rulesSource) {
        this.rulesSource = rulesSource;
    }

    public String[] getManagerCapabilities() {
        return managerCapabilities;
    }
}
