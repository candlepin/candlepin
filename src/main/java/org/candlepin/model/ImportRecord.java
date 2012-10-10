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

import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_import_record")
public class ImportRecord extends AbstractHibernateObject {

    /**
     * The result status of an import.
     */
    public enum Status {
        SUCCESS,
        FAILURE
    }

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @OneToOne
    @JoinColumn(name = "owner_id", nullable = true)
    private Owner owner;

    private Status status;
    private String statusMessage;
    @Column(name = "file_name", nullable = true)
    private String fileName;
    @Column(name = "generated_by", nullable = true)
    private String generatedBy;
    @Column(name = "generated_date", nullable = true)
    private Date generatedDate;
    @Column(name = "upstream_name", nullable = true)
    private String upstreamName;
    @Column(name = "upstream_id", nullable = true)
    private String upstreamId;
    @Column(name = "upstream_type", nullable = true)
    private String upstreamType;
    @Column(name = "webapp_prefix", nullable = true)
    private String webAppPrefix;

    @SuppressWarnings("unused")
    private ImportRecord() {
        // JPA
    }

    public ImportRecord(Owner owner) {
        this.owner = owner;
    }

    @Override
    public Serializable getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the owner
     */
    @XmlTransient
    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public Status getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void recordStatus(Status status, String message) {
        this.status = status;
        this.statusMessage = message;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String name) {
        this.fileName = name;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    public Date getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(Date generatedDate) {
        this.generatedDate = generatedDate;
    }

    public String getUpstreamName() {
        return upstreamName;
    }

    public void setUpstreamName(String upstreamName) {
        this.upstreamName = upstreamName;
    }

    public String getUpstreamType() {
        return upstreamType;
    }

    public void setUpstreamType(String upstreamType) {
        this.upstreamType = upstreamType;
    }

    public String getUpstreamId() {
        return upstreamId;
    }

    public void setUpstreamId(String upstreamId) {
        this.upstreamId = upstreamId;
    }

    public String getWebAppPrefix() {
        return webAppPrefix;
    }

    public void setWebAppPrefix(String webAppPrefix) {
        this.webAppPrefix = webAppPrefix;
    }


}
