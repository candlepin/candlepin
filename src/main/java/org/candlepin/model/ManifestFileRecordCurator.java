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

import org.candlepin.sync.file.ManifestFileType;

import com.google.inject.persist.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Date;

import javax.inject.Singleton;
import javax.persistence.Query;
import javax.sql.rowset.serial.SerialBlob;

/**
 * Provides DB management for stored manifest archive files.
 */
@Singleton
public class ManifestFileRecordCurator extends AbstractHibernateCurator<ManifestFileRecord> {

    private static final String QUERY_CLASS_NAME = ManifestFileRecord.class.getCanonicalName();

    public ManifestFileRecordCurator() {
        super(ManifestFileRecord.class);
    }

    @Transactional
    public boolean deleteById(String id) {
        String queryString = String.format("delete from %s where id=:id", QUERY_CLASS_NAME);
        Query q = getEntityManager().createQuery(queryString);
        q.setParameter("id", id);
        return q.executeUpdate() > 0;
    }

    // Need the caller to start the transaction since large object
    // streaming must be done in the same transaction as the object
    // was looked up in.
    public ManifestFileRecord findFile(String id) {
        return id == null ? null : this.getEntityManager().find(ManifestFileRecord.class, id);
    }

    @Transactional
    public ManifestFileRecord createFile(ManifestFileType type, File fileToStore,
        String principalName, String targetId) throws IOException {
        Blob data = createBlob(fileToStore);

        ManifestFileRecord manifestFileRecord =
            new ManifestFileRecord(type, fileToStore.getName(), principalName, targetId, data);
        this.getEntityManager().persist(manifestFileRecord);

        return manifestFileRecord;
    }

    private Blob createBlob(File fileToStore) throws IOException {
        byte[] fileBytes = new byte[(int) fileToStore.length()];
        try (FileInputStream inputStream = new FileInputStream(fileToStore)) {
            inputStream.read(fileBytes);
        }
        try {
            return new SerialBlob(fileBytes);
        }
        catch (SQLException e) {
            throw new IOException("Error creating Blob from file bytes", e);
        }
    }

    @Transactional
    public int deleteExpired(Date expiryDate) {
        String queryString = String.format("delete from %s r where r.created < :expiry", QUERY_CLASS_NAME);
        Query q = getEntityManager().createQuery(queryString);
        q.setParameter("expiry", expiryDate);
        return q.executeUpdate();
    }

    public int deleteMatching(ManifestFileType type, String targetId) {
        String queryString = String.format("delete from %s r where r.type=:type and r.targetId=:target",
            QUERY_CLASS_NAME);
        Query q = getEntityManager().createQuery(queryString);
        q.setParameter("type", type);
        q.setParameter("target", targetId);
        return q.executeUpdate();
    }

}
