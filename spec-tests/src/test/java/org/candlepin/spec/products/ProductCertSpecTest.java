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
package org.candlepin.spec.products;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductCertificateDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;
import org.candlepin.spec.bootstrap.data.builder.OID;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;

@SpecTest
public class ProductCertSpecTest {

    private static ApiClient adminClient;

    private OwnerDTO owner;
    private ProductDTO product;
    private X509Certificate certificate;

    @BeforeAll
    public static void beforeAll() {
        adminClient = ApiClients.admin();
    }

    @BeforeEach
    public void beforeEach() {
        owner = adminClient.owners().createOwner(Owners.random());
        product = adminClient.ownerProducts().createProduct(owner.getKey(), Products.randomEng());
        ProductCertificateDTO productCertificate = adminClient.ownerProducts()
            .getProductCertificateById(owner.getKey(), product.getId());
        certificate = X509Cert.parseCertificate(productCertificate.getCert());
    }

    @Test
    public void shouldBeValidCert() {
        assertThat(certificate)
            .isNotNull();
    }

    @Test
    public void shouldHaveTheCorrectProductName() {
        byte[] extensionValue = certificate.getExtensionValue(OID.productName(product));
        String productName = new String(extensionValue);
        assertThat(productName)
            .contains(product.getName());
    }
}
