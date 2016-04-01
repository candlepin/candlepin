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
package org.candlepin.sync;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.config.ConfigProperties;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

import java.io.File;
import java.io.IOException;


/**
 * SyncUtils
 */
class SyncUtils {

    private final File baseDir;
    SyncUtils(Configuration config) {
        baseDir = new File(config.getString(ConfigProperties.SYNC_WORK_DIR));
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IseException(
                "Unable to create base dir for sync: " + baseDir);
        }
    }

    static ObjectMapper getObjectMapper(Configuration config) {
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary =
            new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);

        mapper.setAnnotationIntrospector(pair);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // Filter specific things we do not want exported:
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setFailOnUnknownId(false);
        filterProvider = filterProvider.addFilter("EntitlementFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("consumer"));
        mapper.setFilters(filterProvider);

        if (config != null) {
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                config.getBoolean(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES));
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

    File tempFileReference(String name) throws IOException {
        return new File(baseDir, name);
    }

}
