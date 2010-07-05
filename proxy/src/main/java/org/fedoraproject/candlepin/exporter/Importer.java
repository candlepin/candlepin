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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.AbstractHibernateCurator;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Persisted;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.RulesCurator;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

/**
 * Importer
 */
public class Importer {
    private static Logger log = Logger.getLogger(Importer.class);
    
    /**
     * 
     * files we use to perform import
     */
    enum ImportFile {
        CONSUMER_TYPE("consumer_types"),
        CONSUMER("consumer.json"),
        ENTITLEMENTS("entitlements"),
        ENTITLEMENT_CERTIFICATES("entitlement_certificates"),
        PRODUCTS("products"),
        RULES("rules");
        
        private String fileName;
        ImportFile(String fileName) {
            this.fileName = fileName;
        }
        
        public String fileName() {
            return fileName;
        }
        
    }

    private ConsumerTypeCurator consumerTypeCurator;
    private ConsumerCurator consumerCurator;
    private ProductCurator productCurator;
    private ObjectMapper mapper;
    private EntitlementCurator entitlementCurator;
    private PoolCurator poolCurator;
    private RulesCurator rulesCurator;
    
    @Inject
    public Importer(ConsumerTypeCurator consumerTypeCurator, 
        ConsumerCurator consumerCurator, ProductCurator productCurator, 
        EntitlementCurator entitlementCurator, PoolCurator poolCurator,
        RulesCurator rulesCurator) {
        this.consumerTypeCurator = consumerTypeCurator;
        this.consumerCurator = consumerCurator;
        this.productCurator = productCurator;
        this.entitlementCurator = entitlementCurator;
        this.poolCurator = poolCurator;
        this.rulesCurator = rulesCurator;
        this.mapper = ExportUtils.getObjectMapper();
    }

    public void loadExport(File exportFile) {
        try {
            File tmpDir = ExportUtils.makeTempDir("import");
            File exportDir = extractArchive(tmpDir, exportFile);
            
            Map<String, File> importFiles = new HashMap<String, File>();
            for (File file : exportDir.listFiles()) {
                importFiles.put(file.getName(), file);
            }
            
            importObjects(importFiles);
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    @Transactional
    public void importObjects(Map<String, File> importFiles) throws IOException {
        
        // owner?
        
        importRules(importFiles.get(ImportFile.RULES.fileName()).listFiles());
        importConsumerTypes(importFiles.get(ImportFile.CONSUMER_TYPE.fileName()).listFiles());
        //importConsumer(importFiles.get(ImportFile.CONSUMER.fileName()));
        //importProducts(importFiles.get(ImportFile.PRODUCTS.fileName()).listFiles());
        //importEntitlements(
        //    importFiles.get(ImportFile.ENTITLEMENTS.fileName()).listFiles(),
        //   importFiles.get(ImportFile.ENTITLEMENT_CERTIFICATES.fileName()).listFiles());        
        
        // update product with content
        
    }
    
    public void importRules(File[] rulesFiles) throws IOException {
        RulesImporter importer = new RulesImporter(rulesCurator);
        
        // Only importing a single rules file now.
        Reader reader = null;
        try {
            reader = new FileReader(rulesFiles[0]);
            importer.importObject(reader);
        }
        finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
    
    public void importConsumerTypes(File[] consumerTypes) throws IOException {
        ConsumerTypeImporter importer = new ConsumerTypeImporter(consumerTypeCurator);
        Set<ConsumerType> consumerTypeObjs = new HashSet<ConsumerType>();
        for (File consumerType : consumerTypes) {
            Reader reader = null;
            try {
                reader = new FileReader(consumerType);
                consumerTypeObjs.add(importer.createObject(mapper, reader));
            } 
            finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
        importer.store(consumerTypeObjs);
    }

    public void importConsumer(File consumer) throws IOException {
        ConsumerImporter importer = new ConsumerImporter();
        createEntity(importer, consumerCurator, consumer);
    }
    
    public void importProducts(File[] products) throws IOException {
        ProductImporter importer = new ProductImporter();
        Set<Product> productsToImport = new HashSet<Product>();
        for (File product : products) {
            // Skip product.pem's, we just need the json to import:
            if (product.getName().endsWith(".json")) {
                log.debug("Import product: " + product.getName());
                Reader reader = null;
                try {
                    reader = new FileReader(product);
                    productsToImport.add(importer.createObject(mapper, reader));
                }
                finally {
                    if (reader != null) {
                        reader.close();
                    }
                }
            }
        }
        importer.store(productsToImport);
    }
    
    public void importEntitlements(File[] entitlements, File[] entitlementCertificates) 
        throws IOException {
        
        Map<BigInteger, EntitlementCertificate> certs 
            = importCertificates(entitlementCertificates);
        EntitlementImporter importer = new EntitlementImporter();
        for (File entitlement : entitlements) {
            createEntitlement(importer, entitlement, certs);
        }
    }
    
    public Map<BigInteger, EntitlementCertificate> importCertificates(
            File[] entitlementCertificates) throws IOException {
        
        EntitlementCertImporter importer = new EntitlementCertImporter();        
        Map<BigInteger, EntitlementCertificate> toReturn 
            = new HashMap<BigInteger, EntitlementCertificate>();
        
        for (File certificate : entitlementCertificates) {
            Reader reader = null;
            try {
                reader = new FileReader(certificate);
                EntitlementCertificate cert = importer.createObject(mapper, reader);
                toReturn.put(cert.getSerial(), cert);
            } 
            finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
        
        return toReturn;
    }
    
    public void createEntitlement(EntitlementImporter importer, File entitlement,
        Map<BigInteger, EntitlementCertificate> certs) 
        throws IOException {
        
        Reader reader = null;
        try {
            reader = new FileReader(entitlement);
            Object[] parsed = importer.importObject(mapper, reader, certs);
            Entitlement e = (Entitlement) parsed[0];
            Pool p = (Pool) parsed[1];
            e.setPool(p);
            
            poolCurator.create(p);
            entitlementCurator.create(e);
        } 
        finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private <T extends Persisted> void createEntity(
        EntityImporter<T> importer, AbstractHibernateCurator<T> curator, File file)
        throws FileNotFoundException, IOException {
        
        Reader reader = null;
        try {
            reader = new FileReader(file);
            T type = importer.createObject(mapper, reader);
            curator.create(type);
        } 
        finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
    /**
     * Create a tar.gz archive of the exported directory.
     *
     * @param exportDir Directory where Candlepin data was exported.
     * @return File reference to the new archive tar.gz.
     */
    private File extractArchive(File tempDir, File exportFile) {
        log.info("Extracting archive to: " + tempDir.getAbsolutePath());
        ProcessBuilder cmd = new ProcessBuilder("tar", "xvfz",
            exportFile.getAbsolutePath());
        cmd.directory(tempDir);
        try {
            Process p = cmd.start();
            p.waitFor();
            log.debug("Done extracting archive");
        }
        catch (IOException e) {
            // TODO
        }
        catch (InterruptedException e) {
            // TODO
        }
        
        File extractDir = new File(tempDir.getAbsolutePath(), "export");
        return extractDir;
    }
}
