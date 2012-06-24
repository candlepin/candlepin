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
package org.candlepin.model.test;

import junit.framework.Assert;

import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * ProductCertificateCuratorTest
 */
public class ProductCertificateCuratorTest extends DatabaseTestFixture {

    private Product product;

    @Before
    public void init() {
        super.init();

        Product product = new Product("dummy", "Dummy Product");
        this.product = productCurator.create(product);
    }

    @Test
    public void emptyFindForProduct() {
        Assert.assertNull(productCertificateCurator.findForProduct(product));
    }

    @Test
    public void nullForNull() {
        Assert.assertNull(productCertificateCurator.findForProduct(null));
    }

    @Test
    public void validFindForProduct() {
        ProductCertificate cert = new ProductCertificate();
        cert.setProduct(product);
        cert.setKey("key");
        cert.setCert("cert");

        productCertificateCurator.create(cert);

        Assert.assertEquals(cert, productCertificateCurator.findForProduct(product));
    }
}
