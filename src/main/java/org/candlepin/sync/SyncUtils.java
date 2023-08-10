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
package org.candlepin.sync;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.exceptions.IseException;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;



/**
 * SyncUtils
 */
public class SyncUtils {
    private Configuration config;

    File makeTempDir(String baseName) throws IOException {
        File baseDir = new File(config.getString(ConfigProperties.SYNC_WORK_DIR));
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IseException("Unable to create base dir for sync: " + baseDir);
        }

        File tmp = File.createTempFile(baseName, Long.toString(System.nanoTime()), baseDir);

        if (!tmp.delete()) {
            throw new IOException("Could not delete temp file: " + tmp.getAbsolutePath());
        }

        if (!tmp.mkdirs()) {
            throw new IOException("Could not create temp directory: " + tmp.getAbsolutePath());
        }

        return (tmp);
    }

    @Inject
    public SyncUtils(Configuration config) {
        this.config = config;
    }
}
