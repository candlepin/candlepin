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
package org.candlepin.resource.test;


import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Product;
import org.candlepin.model.Subscription;
import org.candlepin.resource.ProductResource;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


/**
 * ProductResourceTest
 */
public class ProductResourceTest extends DatabaseTestFixture {

    private ProductResource productResource;

    @Before
    public void setUp() {

        productResource = injector.getInstance(ProductResource.class);
        contentCurator = injector.getInstance(ContentCurator.class);
    }

    private Product createProduct() {
        String label = "test_product";
        String name = "Test Product";
        String variant = "server";
        String version = "1.0";
        String arch = "ALL";
        String type = "SVC";
        Product prod = new Product(label, name, variant,
                version, arch, type);
        return prod;

    }


    @Test
    public void testCreateProductResource() {

        Product toSubmit = createProduct();
        productResource.createProduct(toSubmit);

    }

    @Test
    public void testCreateProductWithContent() {
        Product toSubmit = createProduct();
        String  contentHash = String.valueOf(
            Math.abs(Long.valueOf("test-content".hashCode())));
        Content testContent = new Content("test-content", contentHash,
                            "test-content-label", "yum", "test-vendor",
                             "test-content-url", "test-gpg-url");

        HashSet<Content> contentSet = new HashSet<Content>();
        testContent = contentCurator.create(testContent);
        contentSet.add(testContent);
        toSubmit.setContent(contentSet);

        productResource.createProduct(toSubmit);
    }

    @Test(expected = BadRequestException.class)
    public void testDeleteProductWithSubscriptions() {
        ProductServiceAdapter pa = mock(ProductServiceAdapter.class);
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        ProductResource pr = new ProductResource(pa, null, null, null, i18n);
        Product p = mock(Product.class);
        when(pa.getProductById(eq("10"))).thenReturn(p);
        Set<Subscription> subs = new HashSet<Subscription>();
        Subscription s = mock(Subscription.class);
        subs.add(s);
        when(pa.productHasSubscriptions(eq(p))).thenReturn(true);

        pr.deleteProduct("10");
    }


}
