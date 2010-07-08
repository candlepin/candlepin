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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.ContentCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;

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
    private ProductCurator productCurator;
    private ObjectMapper mapper;
    private PoolCurator poolCurator;
    private RulesCurator rulesCurator;
    private OwnerCurator ownerCurator;
    private ContentCurator contentCurator;
    private SubscriptionCurator subCurator;
    
    @Inject
    public Importer(ConsumerTypeCurator consumerTypeCurator, ProductCurator productCurator, 
        PoolCurator poolCurator, RulesCurator rulesCurator, OwnerCurator ownerCurator, 
        ContentCurator contentCurator, SubscriptionCurator subCurator) {
        this.consumerTypeCurator = consumerTypeCurator;
        this.productCurator = productCurator;
        this.poolCurator = poolCurator;
        this.rulesCurator = rulesCurator;
        this.ownerCurator = ownerCurator;
        this.contentCurator = contentCurator;
        this.subCurator = subCurator;
        this.mapper = ExportUtils.getObjectMapper();
    }

    public void loadExport(Owner owner, File exportFile) {
        try {
            File tmpDir = ExportUtils.makeTempDir("import");
            File exportDir = extractArchive(tmpDir, exportFile);
            
            Map<String, File> importFiles = new HashMap<String, File>();
            for (File file : exportDir.listFiles()) {
                importFiles.put(file.getName(), file);
            }
            
            importObjects(owner, importFiles);
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ImporterException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    @Transactional
    public void importObjects(Owner owner, Map<String, File> importFiles) throws IOException, ImporterException {
        
        importConsumer(owner, importFiles.get(ImportFile.CONSUMER.fileName()));
        importRules(importFiles.get(ImportFile.RULES.fileName()).listFiles());
        importConsumerTypes(importFiles.get(ImportFile.CONSUMER_TYPE.fileName()).listFiles());
        Set<Product> importedProducts =
            importProducts(importFiles.get(ImportFile.PRODUCTS.fileName()).listFiles());
        importEntitlements(owner, importedProducts,
            importFiles.get(ImportFile.ENTITLEMENTS.fileName()).listFiles());
        
        poolCurator.refreshPools(owner);
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

    public void importConsumer(Owner owner, File consumerFile) throws IOException, ImporterException {
        ConsumerImporter importer = new ConsumerImporter(ownerCurator);
        Reader reader = null;
        try {
            reader = new FileReader(consumerFile);
            ConsumerDto consumer = importer.createObject(mapper, reader);
            importer.store(owner, consumer);
        }
        finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
    
    public Set<Product> importProducts(File[] products) throws IOException {
        ProductImporter importer = new ProductImporter(productCurator, contentCurator);
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
        // TODO: Do we need to cleanup unused products? Looked at this earlier and it
        // looks somewhat complex and a little bit dangerous, so we're leaving them
        // around for now.
        
        return productsToImport;
    }
    
    public void importEntitlements(Owner owner, Set<Product> products, File[] entitlements)
        throws IOException, ImporterException { 
        EntitlementImporter importer = new EntitlementImporter(subCurator);

        Map<String, Product> productsById = new HashMap<String, Product>();
        for (Product product : products) {
            productsById.put(product.getId(), product);
        }
        
        Set<Subscription> subscriptionsToImport = new HashSet<Subscription>();
        for (File entitlement : entitlements) {
            Reader reader = null;
            try {
                log.debug("Import entitlement: " + entitlement.getName());
                reader = new FileReader(entitlement);
                subscriptionsToImport.add(importer.importObject(mapper, reader, owner, productsById));
            } 
            finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
        
        importer.store(owner, subscriptionsToImport);
    }
        
    /**
     * Create a tar.gz archive of the exported directory.
     *
     * @param exportDir Directory where Candlepin data was exported.
     * @return File reference to the new archive tar.gz.
     */
    private File extractArchive(File tempDir, File exportFile) {
        log.info("Extracting archive to: " + tempDir.getAbsolutePath());
        try {
            byte[] buf = new byte[1024];
            ZipInputStream zipinputstream = null;
            ZipEntry zipentry;
            zipinputstream = new ZipInputStream(new FileInputStream(exportFile));
    
            zipentry = zipinputstream.getNextEntry();
            while (zipentry != null) {
                //for each entry to be extracted
                String entryName = zipentry.getName();
                System.out.println("entryname " + entryName);
                int n;
                FileOutputStream fileoutputstream;
                File newFile = new File(entryName);
                String directory = newFile.getParent();
                new File(tempDir, directory).mkdirs();
                
                fileoutputstream = new FileOutputStream(tempDir.getAbsolutePath() +
                    entryName);
    
                while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
                    fileoutputstream.write(buf, 0, n);
                }
    
                fileoutputstream.close(); 
                zipinputstream.closeEntry();
                zipentry = zipinputstream.getNextEntry();
    
            }
    
            zipinputstream.close();
        }
        catch (IOException e) {
             // TODO: handle this
            e.printStackTrace();
        }
        File extractDir = new File(tempDir.getAbsolutePath(), "export");
        return extractDir;
    }
}
