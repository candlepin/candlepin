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
package org.fedoraproject.candlepin.service.impl.test;

import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCertificate;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.impl.BouncyCastlePKIReader;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Assert;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

/**
 * DefaultProductServiceAdapterTest
 */
public class ProductCertCreationTest extends DatabaseTestFixture {

    @Override
    protected Module getGuiceOverrideModule() {
        return new ProductCertCreationModule();
    }

    @Test
    public void hasCert() {
        ProductCertificate cert = createDummyCert();

        Assert.assertTrue(cert.getCert().length() > 0);
    }

    @Test
    public void hasKey() {
        ProductCertificate cert = createDummyCert();

        Assert.assertTrue(cert.getKey().length() > 0);
    }

    @Test
    public void validProduct() {
        Product product = new Product("50", "Test Product",
            "Standard", "1", "x86_64", "Base");
        ProductCertificate cert = createCert(product);

        Assert.assertEquals(product, cert.getProduct());
    }

    @Test(expected = IllegalArgumentException.class)
    public void noHashCreation() {
        createCert(new Product("thin", "Not Much Here"));
    }

    private ProductCertificate createDummyCert() {
        Product product = new Product("50", "Test Product",
            "Standard", "1", "x86_64", "Base");

        return createCert(product);
    }

    private ProductCertificate createCert(Product product) {
        this.productAdapter.createProduct(product);
        return this.productAdapter.getProductCertificate(product);
    }

    private static class ProductCertCreationModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(PKIReader.class).to(BouncyCastlePKIReader.class).asEagerSingleton();
        }
    }

}
