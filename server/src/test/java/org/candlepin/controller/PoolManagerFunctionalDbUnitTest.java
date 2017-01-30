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
package org.candlepin.controller;

import org.candlepin.audit.EventSink;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.Rules;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.dbunit.database.DatabaseSequenceFilter;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.filter.ExcludeTableFilter;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;


public class PoolManagerFunctionalDbUnitTest extends DatabaseTestFixture {
    public static final String PRODUCT_MONITORING = "monitoring";
    public static final String PRODUCT_PROVISIONING = "provisioning";
    public static final String PRODUCT_VIRT_HOST = "virtualization_host";
    public static final String PRODUCT_VIRT_HOST_PLATFORM = "virtualization_host_platform";
    public static final String PRODUCT_VIRT_GUEST = "virt_guest";

    @Inject private CandlepinPoolManager poolManager;

    private Product virtHost;
    private Product virtHostPlatform;
    private Product virtGuest;
    private Product monitoring;
    private Product provisioning;
    private Product socketLimitedProduct;

    private Subscription sub4;

    private ConsumerType systemType;

    private Owner o;
    private Consumer parentSystem;
    private Consumer childVirtSystem;
    private EventSink eventSink;



    public void setUp() throws Exception {
        IDataSet ds = getDataSet("poolManagerFunctionalSetUp.xml");
        DatabaseOperation.INSERT.execute(dbunitConnection, ds);

        o = ownerCurator.find((String)ds.getTable(Owner.DB_TABLE).getValue(0, Owner.ID_COLUMN));
    }

    @Before
    public void setUpOld() throws Exception {
        o = createOwner();
        ownerCurator.create(o);

        virtHost = TestUtil.createProduct(PRODUCT_VIRT_HOST, PRODUCT_VIRT_HOST);
        virtHostPlatform = TestUtil.createProduct(PRODUCT_VIRT_HOST_PLATFORM, PRODUCT_VIRT_HOST_PLATFORM);
        virtGuest = TestUtil.createProduct(PRODUCT_VIRT_GUEST, PRODUCT_VIRT_GUEST);
        monitoring = TestUtil.createProduct(PRODUCT_MONITORING, PRODUCT_MONITORING);
        monitoring.addAttribute(new ProductAttribute("multi-entitlement", "yes"));

        provisioning = TestUtil.createProduct(PRODUCT_PROVISIONING, PRODUCT_PROVISIONING);
        provisioning.addAttribute(new ProductAttribute("multi-entitlement", "yes"));
        provisioning.setMultiplier(2L);
        provisioning.addAttribute(new ProductAttribute("instance-multiplier", "4"));

        virtHost.addAttribute(new ProductAttribute(PRODUCT_VIRT_HOST, ""));
        virtHostPlatform.addAttribute(new ProductAttribute(PRODUCT_VIRT_HOST_PLATFORM, ""));
        virtGuest.addAttribute(new ProductAttribute(PRODUCT_VIRT_GUEST, ""));
        monitoring.addAttribute(new ProductAttribute(PRODUCT_MONITORING, ""));
        provisioning.addAttribute(new ProductAttribute(PRODUCT_PROVISIONING, ""));

        socketLimitedProduct = TestUtil.createProduct("socket-limited-prod", "Socket Limited Product");
        socketLimitedProduct.addAttribute(new ProductAttribute("sockets", "2"));
        productCurator.create(socketLimitedProduct);

        productCurator.create(virtHost);
        productCurator.create(virtHostPlatform);
        productCurator.create(virtGuest);
        productCurator.create(monitoring);
        productCurator.create(provisioning);

        List<Subscription> subscriptions = new LinkedList<Subscription>();

        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

        Subscription sub1 = TestUtil.createSubscription(o, virtHost, new HashSet<Product>());
        sub1.setId(Util.generateDbUUID());
        sub1.setQuantity(5L);
        sub1.setStartDate(new Date());
        sub1.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub1.setModified(new Date());

        Subscription sub2 = TestUtil.createSubscription(o, virtHostPlatform, new HashSet<Product>());
        sub2.setId(Util.generateDbUUID());
        sub2.setQuantity(5L);
        sub2.setStartDate(new Date());
        sub2.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub2.setModified(new Date());

        Subscription sub3 = TestUtil.createSubscription(o, monitoring, new HashSet<Product>());
        sub3.setId(Util.generateDbUUID());
        sub3.setQuantity(5L);
        sub3.setStartDate(new Date());
        sub3.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub3.setModified(new Date());

        sub4 = TestUtil.createSubscription(o, provisioning, new HashSet<Product>());
        sub4.setId(Util.generateDbUUID());
        sub4.setQuantity(5L);
        sub4.setStartDate(new Date());
        sub4.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub4.setModified(new Date());
        sub4.getBranding().add(new Branding("product1", "type1", "branding1"));
        sub4.getBranding().add(new Branding("product2", "type2", "branding2"));

        subscriptions.add(sub1);
        subscriptions.add(sub2);
        subscriptions.add(sub3);
        subscriptions.add(sub4);

        poolManager.getRefresher(subAdapter).add(o).run();

        this.systemType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(systemType);

        parentSystem = new Consumer("system", "user", o, systemType);
        parentSystem.getFacts().put("total_guests", "0");
        consumerCurator.create(parentSystem);

        childVirtSystem = new Consumer("virt system", "user", o, systemType);

        consumerCurator.create(childVirtSystem);
    }

    @Test
    public void nothing() {
        //TODO remove
    }

    /*@Ignore*/ @Test
    public void writeFullDataSet() throws Exception {

        beginTransaction();
        entityManager().createNativeQuery("SET DATABASE REFERENTIAL INTEGRITY FALSE").executeUpdate();
        commitTransaction();

        IDataSet ds = dbunitConnection.createDataSet();

        //when we try to touch liquibase tables, you get exception
        ExcludeTableFilter excludeTableFilter = new ExcludeTableFilter(new String [] {Rules.DB_TABLE,
            "databasechangelog", "databasechangeloglock", }); // rules contain huge blob!
        FilteredDataSet fds = new FilteredDataSet(excludeTableFilter, ds);

        //brings database tables in a specific order (avoid FK constraint violation)
        DatabaseSequenceFilter sequenceFilter = new DatabaseSequenceFilter(dbunitConnection);
        FilteredDataSet fds2 = new FilteredDataSet(sequenceFilter, fds);

        FlatXmlDataSet.write(fds2, new FileOutputStream("src/test/resources/datasets/full.xml"));
    }

}

