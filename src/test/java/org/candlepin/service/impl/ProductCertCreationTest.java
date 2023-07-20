/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.pki.CertificateReader;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



/**
 * DefaultProductServiceAdapterTest
 */
public class ProductCertCreationTest extends DatabaseTestFixture {
    private ProductServiceAdapter productAdapter;

    @Override
    protected Module getGuiceOverrideModule() {
        return new ProductCertCreationModule();
    }

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();
        productAdapter = injector.getInstance(ProductServiceAdapter.class);
    }

    @Test
    public void hasCert() {
        CertificateInfo cert = createDummyCert();

        assertTrue(cert.getCertificate().length() > 0);
    }

    @Test
    public void hasKey() {
        CertificateInfo cert = createDummyCert();

        assertTrue(cert.getKey().length() > 0);
    }

    @Test
    public void validProduct() {
        Owner owner = TestUtil.createOwner("Example-Corporation");
        Product product = this.createProduct("50", "Test Product", "Standard", "1", "x86_64", "Base");

        CertificateInfo cert = this.createCert(owner, product);
        assertNotNull(cert);
        assertNotNull(cert.getKey());
        assertNotNull(cert.getCertificate());
    }

    @Test
    public void noHashCreation() {
        Owner owner = TestUtil.createOwner("Example-Corporation");
        Product product = TestUtil.createProduct("thin", "Not Much Here");

        CertificateInfo cert = this.createCert(owner, product);

        // Marketing products, or products that don't have a numeric ID (i.e. non-engineering
        // products), will not have certificates.
        assertNull(cert);
    }

    private CertificateInfo createDummyCert() {
        Owner owner = TestUtil.createOwner("Example-Corporation");
        Product product = this.createProduct("50", "Test Product", "Standard", "1", "x86_64", "Base");

        return createCert(owner, product);
    }

    private CertificateInfo createCert(Owner owner, Product product) {
        owner.setId(null);
        product.setUuid(null);

        this.ownerCurator.create(owner);
        this.productCurator.create(product);
        this.ownerProductCurator.mapProductToOwner(product, owner);

        CertificateInfo out = this.productAdapter.getProductCertificate(owner.getKey(), product.getId());

        return out;
    }

    private Product createProduct(String productId, String name, String variant, String version, String arch,
        String type) {

        Product product = TestUtil.createProduct(productId, name);

        product.setAttribute(Product.Attributes.VERSION, version);
        product.setAttribute(Product.Attributes.VARIANT, variant);
        product.setAttribute(Product.Attributes.TYPE, type);
        product.setAttribute(Product.Attributes.ARCHITECTURE, arch);

        return product;
    }

    private static class ProductCertCreationModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(CertificateReader.class).asEagerSingleton();
        }
    }

}
