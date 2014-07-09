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
package org.candlepin.json.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * EntitlementBody
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Subscription {

    private String sku;
    private String name;
    private Integer warning;
    private Integer sockets;
    // RAM is specified in GB.
    private Integer ram;
    private Integer cores;
    private Boolean management;
    @JsonProperty("stacking_id")
    private String stackingId;
    @JsonProperty("virt_only")
    private Boolean virtOnly;
    private Service service;

    /**
     * @param sku
     */
    public void setSku(String sku) {
        this.sku = sku;
    }

    /**
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param warning
     */
    public void setWarning(Integer warning) {
        this.warning = warning;
    }

    /**
     * @param sockets
     */
    public void setSockets(Integer sockets) {
        this.sockets = sockets;
    }

    /**
     * @param ram
     */
    public void setRam(Integer ram) {
        this.ram = ram;
    }

    /**
     * @param cores
     */
    public void setCores(Integer cores) {
        this.cores = cores;
    }

    /**
     * @param management
     */
    public void setManagement(Boolean management) {
        this.management = management;
    }

    /**
     * @param stackingId
     */
    public void setStackingId(String stackingId) {
        this.stackingId = stackingId;
    }

    /**
     * @param virt_only
     */
    public void setVirtOnly(Boolean virtOnly) {
        this.virtOnly = virtOnly;
    }

    /**
     * @param service
     */
    public void setService(Service service) {
        this.service = service;
    }
}
