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

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.candlepin.model.Rules.RulesSourceEnum;
import org.candlepin.util.ManagerCapability;

/**
 * Status
 */
@XmlRootElement(name = "status")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Status {

    private boolean result;
    private String version;
    private String rulesVersion;
    private String release;
    private boolean standalone;
    private Date timeUTC;
    private String[] managerCapabilities;

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
        this.managerCapabilities = ManagerCapability.getCapabilityList();
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
