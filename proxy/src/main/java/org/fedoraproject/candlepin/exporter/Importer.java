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
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.AbstractHibernateCurator;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Persisted;
import org.fedoraproject.candlepin.model.ProductCurator;

import com.google.inject.Inject;

/**
 * Importer
 */
public class Importer {
    
    /**
     * 
     * files we use to perform import
     */
    enum ImportFile {
        CONSUMER_TYPE("consumer_types"),
        CONSUMER("consumer.json"),
        ENTITLEMENTS("entitlements"),
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
    
    @Inject
    public Importer(ConsumerTypeCurator consumerTypeCurator, 
        ConsumerCurator consumerCurator, ProductCurator productCurator) {
        this.consumerTypeCurator = consumerTypeCurator;
        this.consumerCurator = consumerCurator;
        this.productCurator = productCurator;
        this.mapper = Exporter.getObjectMapper();
    }

    public void importObjects(Map<String, File> importFiles) throws IOException {
        
        // owner?
        
        importConsumerTypes(importFiles.get(ImportFile.CONSUMER_TYPE).listFiles());
        importConsumer(importFiles.get(ImportFile.CONSUMER));
        importProducts(importFiles.get(ImportFile.PRODUCTS).listFiles());
        
        
        // import entitlement certs & generate content sets
        // update product with content
        // import rules
        
    }
    
    public void importConsumerTypes(File[] consumerTypes) throws IOException {
        ConsumerTypeImporter importer = new ConsumerTypeImporter();
        for (File consumerType : consumerTypes) {
            createEntity(importer, consumerTypeCurator, consumerType);
        }
    }

    public void importConsumer(File consumer) throws IOException {
        ConsumerImporter importer = new ConsumerImporter();
        createEntity(importer, consumerCurator, consumer);
    }
    
    public void importProducts(File[] products) throws IOException {
        ProductImporter importer = new ProductImporter();
        for (File product : products) {
            createEntity(importer, productCurator, product);
        }
    }
    
    public void importEntitlements(File[] entitlementCertificates) throws IOException {
        for (File entitlementCertificate : entitlementCertificates) {
            
        }
    }
    
    private <T extends Persisted> void createEntity(
        EntityImporter<T> importer, AbstractHibernateCurator<T> curator, File file)
        throws FileNotFoundException, IOException {
        
        Reader reader = null;
        try {
            reader = new FileReader(file);
            T type = importer.importObject(mapper, reader);
            curator.create(type);
        } 
        finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
    
}
