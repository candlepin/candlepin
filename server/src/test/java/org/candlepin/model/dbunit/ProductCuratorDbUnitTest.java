/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.model.dbunit;

import static org.candlepin.test.db.ProductHelper.PRODUCT_UUID_VALUE;
import static org.candlepin.test.db.ProductHelper.assertProduct;
import static org.candlepin.test.db.ProductHelper.newProduct;
import static org.junit.Assert.assertNotNull;

import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.db.ProductHelper;

import org.dbunit.Assertion;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

public class ProductCuratorDbUnitTest extends DatabaseTestFixture{


    @Test
    public void testFindProductById() throws Exception {
        IDataSet setupDataSet = getDataSet("products.xml");
        DatabaseOperation.INSERT.execute(dbunitConnection, setupDataSet);

        Product p = productCurator.find(PRODUCT_UUID_VALUE);

        assertProduct(p);
    }

    @Test
    public void testCreateProduct() throws Exception {
        Product p = newProduct();
        p.setUuid(null);
        String oldUuid = p.getUuid();
        Date oldCreated = p.getCreated();
        Date oldUpdated = p.getUpdated();

        p = productCurator.create(p);

        assertNotNull(p);
        Assert.assertNotEquals(oldUuid, p.getUuid());
        Assert.assertNotEquals(oldCreated, p.getCreated());
        Assert.assertNotEquals(oldUpdated, p.getUpdated());

        ITable expectedProducts = getDataSet("products.xml").getTable(Product.DB_TABLE);
        ITable actualProducts = dbunitConnection.createTable(Product.DB_TABLE);
        Assertion.assertEqualsIgnoreCols(expectedProducts, actualProducts, ProductHelper.ignoredCols);
    }
}
