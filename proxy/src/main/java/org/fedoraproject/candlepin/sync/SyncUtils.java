/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.sync;

import java.io.File;
import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.fedoraproject.candlepin.resteasy.JsonProvider;

/**
 * SyncUtils
 */
class SyncUtils {

    private final File baseDir;
    SyncUtils(Config config) {
        baseDir = new File(config.getString(ConfigProperties.SYNC_WORK_DIR));
        if (!baseDir.exists()) {
            if (!baseDir.mkdirs()) {
                throw new IseException(
                    "Unable to create base dir for sync: " + baseDir);
            }
        }
    }
    
    static ObjectMapper getObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JsonProvider.configureObjectMapper(mapper);
        return mapper;
    }

    File makeTempDir(String baseName) throws IOException {
        File tmp = File.createTempFile(baseName, Long.toString(System.nanoTime()),
            baseDir);

        if (!tmp.delete()) {
            throw new IOException("Could not delete temp file: " + tmp.getAbsolutePath());
        }

        if (!tmp.mkdirs()) {
            throw new IOException("Could not create temp directory: " + 
                tmp.getAbsolutePath());
        }

        return (tmp);
    }
}
