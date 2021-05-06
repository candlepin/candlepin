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
package org.candlepin.sync.file;

import org.candlepin.model.ManifestFileRecordCurator;

import com.google.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * A ManifestFileService implementation that stores manifest files in a DB.
 */
public class DBManifestService implements ManifestFileService {

    private ManifestFileRecordCurator curator;

    @Inject
    public DBManifestService(ManifestFileRecordCurator curator) {
        this.curator = curator;
    }

    @Override
    public ManifestFile get(String id) throws ManifestFileServiceException {
        return curator.findFile(id);
    }

    @Override
    public boolean delete(String id) throws ManifestFileServiceException {
        return curator.deleteById(id);
    }

    @Override
    public ManifestFile store(ManifestFileType type, File fileToStore, String principalName,
        String targetId) throws ManifestFileServiceException {
        try {
            return curator.createFile(type, fileToStore, principalName, targetId);
        }
        catch (IOException e) {
            throw new ManifestFileServiceException(e);
        }
    }

    @Override
    public int deleteExpired(Date expiryDate) {
        return curator.deleteExpired(expiryDate);
    }

    @Override
    public int delete(ManifestFileType type, String targetId) {
        return curator.deleteMatching(type, targetId);
    }

}
