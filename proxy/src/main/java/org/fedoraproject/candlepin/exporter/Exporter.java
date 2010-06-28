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
package org.fedoraproject.candlepin.exporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Consumer;

/**
 * Exporter
 */
public class Exporter {
    // XXX: make this configurable
    private static final String WORK_DIR = "/tmp/candlepin/exports";
    private static Logger log = Logger.getLogger(Exporter.class);
    
    private ObjectMapper mapper;

    private MetaExporter meta;
    private ConsumerExporter consumer;
    private RulesExporter rules;
    
    public Exporter() {
        mapper = getObjectMapper();
        
        meta = new MetaExporter(mapper);
        consumer = new ConsumerExporter(mapper);
//        rules = new RulesExporter();
    }

    static ObjectMapper getObjectMapper() {
        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospector.Pair(primary, secondary);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.getSerializationConfig().setAnnotationIntrospector(pair);
        mapper.getDeserializationConfig().setAnnotationIntrospector(pair);
        
        return mapper;
    }
    
    public File getExport(Consumer consumer) {
        try {
            File baseDir = makeTempDir();
            
            exportMeta(baseDir);
            exportConsumer(baseDir, consumer);
            exportEntitlements(baseDir);
            exportProducts(baseDir);
            exportConsumerTypes(baseDir);
            exportRules(baseDir);
            return makeArchive(baseDir);
 //           FileUtils.deleteDirectory(baseDir);
        }
        catch (IOException e) {
            // XXX: deal with this.
            e.printStackTrace();
        }
        
        // Shouldn't ever hit this...
        return null;
    }

    /**
     * Create a tar.gz archive of the exported directory.
     *
     * @param exportDir Directory where Candlepin data was exported.
     * @return File reference to the new archive tar.gz.
     */
    private File makeArchive(File exportDir) {
        String exportFileName = exportDir.getName() + ".tar.gz";
        log.info("Creating archive of " + exportDir.getAbsolutePath() + " in: " +
            exportFileName);
        ProcessBuilder cmd = new ProcessBuilder("tar", "cvfz", exportFileName,
            exportDir.getName());
        cmd.directory(new File(WORK_DIR));
        try {
            Process p = cmd.start();
            p.waitFor();
            log.debug("Done creating: " + exportFileName);
        }
        catch (IOException e) {
            // TODO
        }
        catch (InterruptedException e) {
            // TODO
        }
        File archive = new File(WORK_DIR, exportFileName);
        log.debug("Returning file: " + archive.getAbsolutePath());
        return archive;
    }

    private void exportMeta(File baseDir) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "meta.json");
        FileWriter writer = new FileWriter(file);
        meta.export(writer);
        writer.close();
    }
    
    private void exportConsumer(File baseDir, Consumer consumer) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "consumer.json");
        FileWriter writer = new FileWriter(file);
        this.consumer.export(consumer, writer);
        writer.close();
    }

    private void exportEntitlements(File baseDir) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "entitlements");
        file.mkdir();
    }
    
    private void exportProducts(File baseDir) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "products");
        file.mkdir();
    }
    
    private void exportConsumerTypes(File baseDir) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "consumer_types");
        file.mkdir();
        
/*        file = new File(file.getCanonicalPath(), "rules.js");
        FileWriter writer = new FileWriter(file);
        rules.export(writer);
        writer.close();
*/    }

    private void exportRules(File baseDir) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "rules");
        file.mkdir();
        
        file = new File(file.getCanonicalPath(), "rules.js");
        FileWriter writer = new FileWriter(file);
        rules.export(writer);
        writer.close();
    }
    
    private File makeTempDir() throws IOException {
        // TODO: Need to make sure WORK_DIR exists:
        File tmp = File.createTempFile("export", Long.toString(System.nanoTime()),
            new File(WORK_DIR));

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
