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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;



/**
 * ProductCertificateCuratorTest
 */
public class ProductCertificateCuratorTest extends DatabaseTestFixture {

    @Test
    public void emptyFindForProduct() {
        Product product = this.createProduct();
        assertNull(productCertificateCurator.findForProduct(product));
    }

    @Test
    public void nullForNull() {
        assertNull(productCertificateCurator.findForProduct(null));
    }

    @Test
    public void validFindForProduct() {
        Product product = this.createProduct();

        ProductCertificate cert = new ProductCertificate();
        cert.setProduct(product);
        cert.setKey("key");
        cert.setCert("cert");

        productCertificateCurator.create(cert);

        assertEquals(cert, productCertificateCurator.findForProduct(product));
    }
}
