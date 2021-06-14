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

import org.candlepin.sync.file.ManifestFile;
import org.candlepin.sync.file.ManifestFileType;

import org.hibernate.annotations.GenericGenerator;

import java.beans.Transient;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlTransient;

/**
 * A class representing the storage of a manifest file and the meta-data associated
 * with it. A ManifestRecord object is the persistent bridge between candlepin and
 * the implemented {@link ManifestFileService}.
 */
@Entity
@Table(name = ManifestFileRecord.DB_TABLE)
public class ManifestFileRecord extends AbstractHibernateObject implements ManifestFile {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_manifest_file_record";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    /**
     * The type of manifest record - IMPORT/EXPORT.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private ManifestFileType type;

    @Column(name = "principal_name")
    private String principalName;

    /**
     * The tartgetId is the unique ID of the entity being targeted in during
     * an import/export operation. During export the target would be the consumer.
     * During import, the target would be the owner.
     */
    @Column(name = "target_id")
    private String targetId;

    private String filename;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private Blob fileData;

    public ManifestFileRecord() {
        // For hibernate.
    }

    public ManifestFileRecord(ManifestFileType type, String filename, String principalName,
        String targetId, Blob data) {
        this.type = type;
        this.filename = filename;
        this.principalName = principalName;
        this.targetId = targetId;
        this.fileData = data;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ManifestFileType getType() {
        return type;
    }

    public void setType(ManifestFileType type) {
        this.type = type;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public void setPrincipalName(String principalName) {
        this.principalName = principalName;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getFileName() {
        return filename;
    }

    public void setFileName(String fileName) {
        this.filename = fileName;
    }

    @XmlTransient
    public Blob getFileData() {
        return fileData;
    }

    public void setFileData(Blob fileData) {
        this.fileData = fileData;
    }

    @Override
    @Transient
    public String getName() {
        return filename;
    }

    @Override
    @Transient
    public InputStream getInputStream() {
        try {
            return fileData.getBinaryStream();
        }
        catch (SQLException e) {
            throw new RuntimeException("InputStream not available for manifest file.", e);
        }
    }
}
