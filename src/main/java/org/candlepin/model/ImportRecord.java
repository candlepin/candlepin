/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import java.util.Date;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 *
 */
@Entity
@Table(name = ImportRecord.DB_TABLE)
public class ImportRecord extends AbstractHibernateObject {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_import_record";

    /**
     * The result status of an import.
     */
    public enum Status {
        SUCCESS,
        FAILURE,
        DELETE,
        SUCCESS_WITH_WARNING
    }

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @OneToOne
    @JoinColumn(name = "owner_id", nullable = true)
    private Owner owner;

    private Status status;

    @Size(max = 255)
    private String statusMessage;

    @Column(name = "file_name", nullable = true)
    @Size(max = 255)
    private String fileName;

    @Column(name = "generated_by", nullable = true)
    @Size(max = 255)
    private String generatedBy;

    @Column(name = "generated_date", nullable = true)
    private Date generatedDate;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "upstream_id")
    private ImportUpstreamConsumer upstreamConsumer;

    @SuppressWarnings("unused")
    protected ImportRecord() {
        // JPA
    }

    public ImportRecord(Owner owner) {
        this.owner = owner;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the owner
     */
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

    public ImportUpstreamConsumer getUpstreamConsumer() {
        return upstreamConsumer;
    }

    public void setUpstreamConsumer(ImportUpstreamConsumer upstreamConsumer) {
        this.upstreamConsumer = upstreamConsumer;
    }

    @Override
    public String toString() {
        return String.format("ImportRecord [owner: %s, status: %s]", this.getOwner(), this.getStatus());
    }
}
