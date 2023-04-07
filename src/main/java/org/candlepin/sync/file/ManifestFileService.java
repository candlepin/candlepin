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
package org.candlepin.sync.file;

import java.io.File;
import java.util.Date;

/**
 * A service providing access to manifest files that have been
 * imported or exported.
 *
 */
public interface ManifestFileService {

    /**
     * Gets a manifest matching the specified id. A files id should be
     * a unique identifier such as a database entity ID, or a file path.
     *
     * @param id the id of the target manifest.
     * @return a {@link ManifestFile} matching the id, null otherwise.
     * @throws ManifestFileServiceException if there is a service issue while looking for the file.
     */
    ManifestFile get(String id) throws ManifestFileServiceException;

    /**
     * Stores the specified file.
     *
     * @param type the type of operation the file is being stored for (Import/Export)
     * @param fileToStore the {@link File} to store.
     * @param principalName the name of the principal who uploaded the file.
     * @param targetId the id of the target entity (will change based on operation). Import: Owner.id
     *                 Export: Consumer.uuid
     * @return the id of the stored file.
     * @throws ManifestFileServiceException if there is a service issue while storing the file.
     */
    ManifestFile store(ManifestFileType type, File fileToStore, String principalName, String targetId)
        throws ManifestFileServiceException;

    /**
     * Deletes a manifest matching the specified id.
     *
     * @param id the id of the target manifest file.
     * @return true if the file was deleted, false otherwise.
     * @throws ManifestFileServiceException if there is a service issue while deleting the file.
     */
    boolean delete(String id) throws ManifestFileServiceException;

    /**
     * Deletes any manifests files that are older than the specified expiry date.
     *
     * @param expiryDate the target expiry date.
     * @return the number of files deleted.
     * @throws ManifestFileServiceException if there is a service issue while deleting the files.
     */
    int deleteExpired(Date expiryDate) throws ManifestFileServiceException;

    /**
     * Delete all files matching the type and targetId.
     *
     * @param type the type of file.
     * @param targetId the ID of the file target (consumer UUID (export) or Owner key (import))
     * @return the number of files deleted.
     * @throws ManifestFileServiceException if there is a service issue while deleting the files.
     */
    int delete(ManifestFileType type, String targetId) throws ManifestFileServiceException;

}
