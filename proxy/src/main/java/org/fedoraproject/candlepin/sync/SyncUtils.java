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

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.exceptions.IseException;

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

    static ObjectMapper getObjectMapper(Config config) {
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospector.Pair(primary, secondary);

        mapper.getSerializationConfig().setAnnotationIntrospector(pair);
        mapper.getDeserializationConfig().setAnnotationIntrospector(pair);
        mapper.getSerializationConfig().set(
            SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS,
            false);

        if (config != null) {
            mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES,
                config.failOnUnknownImportProperties());
        }

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
