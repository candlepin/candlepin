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

import java.io.InputStream;

/**
 * Represents a manifest file that is stored in the {@link ManifestFileService}.
 */
public interface ManifestFile {

    /**
     * Gets the id of the stored file on the service.
     *
     * @return the id of the file
     */
    String getId();

    /**
     * The filename associated with the stored file.
     *
     * @return the filename of the stored file.
     */
    String getName();

    /**
     * Gets an input stream to the stored file.
     *
     * @return an input stream to the stored file.
     */
    InputStream getInputStream();

    /**
     * Gets the target id of the stored file. If the file was created as part of
     * a consumer export, it should be the consumer UUID. If it was created as part
     * of an import, it will be the owner ID.
     *
     * @return the intended target.
     */
    String getTargetId();

}
