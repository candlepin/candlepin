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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.model.Product;

import com.google.inject.Inject;

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
    private ProductExporter productExporter;
    private ProductCertExporter productCertExporter;
    private ConsumerTypeExporter consumerType;
    private RulesExporter rules;
    private EntitlementCertExporter entCert;
    
    private ConsumerTypeCurator consumerTypeCurator;
    private EntitlementCertServiceAdapter entCertAdapter;
    private ProductServiceAdapter productAdapter;
    
    @Inject
    public Exporter(ConsumerTypeCurator consumerTypeCurator, MetaExporter meta,
        ConsumerExporter consumer, ConsumerTypeExporter consumerType, 
        RulesExporter rules, EntitlementCertExporter entCert,
        EntitlementCertServiceAdapter entCertAdapter, ProductExporter productExporter,
        ProductServiceAdapter productAdapter, ProductCertExporter productCertExporter) {
        
        mapper = getObjectMapper();
        this.consumerTypeCurator = consumerTypeCurator;
        
        this.meta = meta;
        this.consumer = consumer;
        this.consumerType = consumerType;
        this.rules = rules;
        this.entCert = entCert;
        this.entCertAdapter = entCertAdapter;
        this.productExporter = productExporter;
        this.productAdapter = productAdapter;
        this.productCertExporter = productCertExporter;
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
            File tempDir = makeTempDir();
            File baseDir = new File(tempDir.getAbsolutePath(), "export");
            baseDir.mkdir();
            
            exportMeta(baseDir);
            exportConsumer(baseDir, consumer);
            exportEntitlements(baseDir, consumer);
            exportProducts(baseDir, consumer);
            exportConsumerTypes(baseDir);
            exportRules(baseDir);
            return makeArchive(tempDir, baseDir);
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
    private File makeArchive(File tempDir, File exportDir) {
        String exportFileName = exportDir.getName() + ".tar.gz";
        log.info("Creating archive of " + exportDir.getAbsolutePath() + " in: " +
            exportFileName);
        ProcessBuilder cmd = new ProcessBuilder("tar", "cvfz", exportFileName,
            exportDir.getName());
        cmd.directory(tempDir);
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
        File archive = new File(tempDir, exportFileName);
        log.debug("Returning file: " + archive.getAbsolutePath());
        return archive;
    }

    private void exportMeta(File baseDir) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "meta.json");
        FileWriter writer = new FileWriter(file);
        meta.export(mapper, writer);
        writer.close();
    }
    
    private void exportConsumer(File baseDir, Consumer consumer) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "consumer.json");
        FileWriter writer = new FileWriter(file);
        this.consumer.export(mapper, writer, consumer);
        writer.close();
    }

    private void exportEntitlements(File baseDir, Consumer consumer) throws IOException {
        File entCertDir = new File(baseDir.getCanonicalPath(), "entitlements");
        entCertDir.mkdir();

        for (EntitlementCertificate cert : entCertAdapter.listForConsumer(consumer)) {
            log.debug("Exporting entitlement certificate: " + cert.getSerial());
            File file = new File(entCertDir.getCanonicalPath(), cert.getSerial() + ".pem");
            FileWriter writer = new FileWriter(file);
            entCert.export(writer, cert);
            writer.close();
        }
    }
    
    private void exportProducts(File baseDir, Consumer consumer) throws IOException {
        File productDir = new File(baseDir.getCanonicalPath(), "products");
        productDir.mkdir();
        
        Map<String, Product> products = new HashMap<String, Product>();
        for (Entitlement entitlement : consumer.getEntitlements()) {
            
            for (String productId : entitlement.getPool().getProvidedProductIds()) {
                // Don't want to call the adapter if not needed, it can be expensive.
                if (!products.containsKey(productId)) {
                    products.put(productId, productAdapter.getProductById(productId));
                }
            }
        }
        
        for (Product product : products.values()) {
            File file = new File(productDir.getCanonicalPath(), product.getId() + ".json");
            FileWriter writer = new FileWriter(file);
            productExporter.export(mapper, writer, product);
            writer.close();
            
            file = new File(productDir.getCanonicalPath(), product.getId() + ".pem");
            writer = new FileWriter(file);
            productCertExporter.export(writer,
                productAdapter.getProductCertificate(product));
            writer.close();
        }
    }
    
    private void exportConsumerTypes(File baseDir) throws IOException {
        File typeDir = new File(baseDir.getCanonicalPath(), "consumer_types");
        typeDir.mkdir();

        for (ConsumerType type : consumerTypeCurator.listAll()) {
            File file = new File(typeDir.getCanonicalPath(), type.getLabel() + ".json");
            FileWriter writer = new FileWriter(file);
            consumerType.export(mapper, writer, type);
            writer.close();
        }
    }

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
