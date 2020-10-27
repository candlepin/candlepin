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
package org.candlepin.model;

import org.candlepin.test.TestUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * This class creates the following dataset:
 *  - 10 Engineering products E0..E9
 *  - 10 Pools P0..P9
 *  - 10 Marketing products M0..M9
 *  - 3 consumers C0..C2
 *  - 10 Content instances CON0..CON9
 *  - 20 Entitlements E0..E19
 *
 * To understand different edge cases that this dataset provides, see inline comments in this class
 * and most importantly see commented tests in EntitlementCuratorTest
 *
 * @author fnguyen
 * @see EntitlementCuratorTest
 *
 */
public class ModifierTestDataGenerator {
    private static Logger log = LoggerFactory.getLogger(ModifierTestDataGenerator.class);

    /**
     * All the entitlements of this consumer are expired
     */
    @Inject private ProductCurator productCurator;
    @Inject private OwnerProductCurator ownerProductCurator;
    @Inject private CertificateSerialCurator certSerialCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private ContentCurator contentCurator;

    private Owner owner;

    private List<Consumer> consumers = new ArrayList<>();
    private List<Content> contents = new ArrayList<>();
    private List<Product> engProducts = new ArrayList<>();
    private List<Product> mktProducts = new ArrayList<>();
    private List<Entitlement> entitlements = new ArrayList<>();
    private List<Pool> pools = new ArrayList<>();

    public void createTestData(Owner owner) {
        this.owner = owner;

        for (int i = 0; i < 10; i++) {
            Product prod = new Product("E" + i, "EName" + i);
            engProducts.add(prod);
        }

        for (int i = 0; i < 10; i++) {
            Content content = TestUtil.createContent("fakecontent-" + i);
            Set<String> modified = new HashSet<>();

            /**
             * Content 0 modifies every product
             */
            if (i == 0) {
                for (Product p : engProducts) {
                    modified.add(p.getId());
                }
            }
            else if (i == 1) {
                /**
                 * Content 1 modify product 3
                 */
                modified.add(engProducts.get(3).getId());
            }
            else if (i == 4 || i == 5) {
                /**
                 * Content 4,5 modify products 7,8,9
                 */
                modified.add(engProducts.get(7).getId());
                modified.add(engProducts.get(8).getId());
                modified.add(engProducts.get(9).getId());
            }
            else if (i == 6) {
                /**
                 * Content 6 modifies marketing product 2 and 3
                 */
                modified.add("M2");
                modified.add("M3");
            }

            content.setModifiedProductIds(modified);
            content = contentCurator.create(content);
            contents.add(content);
        }

        /**
         * Attach engineering products to content
         */
        for (int i = 0; i < 10; i++) {
            engProducts.get(i).addContent(contents.get(i), true);
        }

        for (Product prod: engProducts) {
            productCurator.create(prod);
            this.ownerProductCurator.mapProductToOwner(prod, owner);
        }

        for (int i = 0; i < 3; i++) {
            Consumer cons = createConsumer(owner, "C" + i);
            consumers.add(cons);
        }

        for (int i = 0; i < 10; i++) {
            List<Product> provided = new ArrayList<>();
            provided.add(engProducts.get(i));
            Date start = TestUtil.createDate(2000, 1, 1);
            Date end = null;

            /**
             * First 3 pools are expired
             */
            if (i < 3) {
                end = TestUtil.createDate(2005, 1, 1);
            }
            else if (i == 7) { //Pool 7 shouldn't overlap
                start = TestUtil.createDate(2051, 1, 1);
                end = TestUtil.createDate(2052, 1, 1);
            }
            else {
                end = TestUtil.createDate(2050, 1, 1);
            }

            //Pool 2 doesn't have provided products
            if (i == 2) {
                provided = new ArrayList<>();
            }

            Pool p = createPool(i, start, end, provided);
            pools.add(p);
        }

        for (int i = 0; i < 20; i++) {
            Entitlement e = null;


            /**
             * Consumer 0 owns entitlements from 0-9 to every pool
             * Consumer 1 owns only one entitlement - 18, to pool 8
             * Consumer 2 owns entitlements to pool 0-9 without 8 and 4
             */
            if (i <= 9) {
                e = createEntitlement(consumers.get(0), pools.get(i % 10));
            }
            else if (i == 18) {
                e = createEntitlement(consumers.get(1), pools.get(i % 10));
            }
            else if (i != 14) {
                e = createEntitlement(consumers.get(2), pools.get(i % 10));
            }


            entitlements.add(e);
        }
    }


    public List<Entitlement> getEntitlements(Integer ... indexes) {
        List<Entitlement> result = new ArrayList<>();
        for (Integer i : indexes) {
            result.add(entitlements.get(i));
        }
        return result;
    }

    private Entitlement createEntitlement(Consumer consumer, Pool pool) {
        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(e);
        return e;
    }

    private EntitlementCertificate createEntitlementCertificate(String key,
        String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(new Date());
        certSerialCurator.create(certSerial);
        toReturn.setKeyAsBytes(key.getBytes());
        toReturn.setCertAsBytes(cert.getBytes());
        toReturn.setSerial(certSerial);
        return toReturn;
    }

    private Pool createPool(int id, Date startDate, Date endDate, List<Product> provided) {
        Product poolProd = TestUtil.createProduct("M" + id, "MName-" + id);
        this.mktProducts.add(poolProd);
        for (Product prov : provided) {
            poolProd.addProvidedProduct(prov);
        }

        poolProd = this.productCurator.create(poolProd);
        this.ownerProductCurator.mapProductToOwner(poolProd, owner);

        Pool p = TestUtil.createPool(owner, poolProd)
            .setStartDate(startDate)
            .setEndDate(endDate)
            .setQuantity(1000L);

        return poolCurator.create(p);
    }

    private Consumer createConsumer(Owner owner, String name) {
        ConsumerType type = new ConsumerType("test-consumer-type-" +
            TestUtil.randomInt());
        consumerTypeCurator.create(type);
        Consumer c = new Consumer(name, "test-user", owner, type);
        consumerCurator.create(c);
        return c;
    }


    public String getEntitlementId(int i) {
        return getEntitlements(i).get(0).getId();
    }

}

