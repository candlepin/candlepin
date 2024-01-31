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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.TestUtil;
import org.candlepin.util.X509ExtensionUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;


// TODO: FIXME: Rewrite this test to not be so reliant upon mocks. It's making things incredibly brittle and
// wasting dev time tracking down non-issues when a mock silently fails because the implementation changes.
// :(

public class DefaultProductServiceAdapterTest {
    private static final String SOME_ID = "deadbeef";

    private ProductCurator productCurator;
    private ProductCertificateCurator productCertificateCurator;
    private PKIUtility pki;
    private KeyPairGenerator keyPairGenerator;
    private DefaultProductServiceAdapter adapter;

    @BeforeEach
    public void init() {
        Configuration config = mock(Configuration.class);
        when(config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING)).thenReturn(false);

        this.productCurator = mock(ProductCurator.class);
        this.pki = mock(PKIUtility.class);
        this.keyPairGenerator = mock(BouncyCastleKeyPairGenerator.class);
        X509ExtensionUtil extUtil = new X509ExtensionUtil(config);
        this.productCertificateCurator = spy(new ProductCertificateCurator(pki, extUtil, keyPairGenerator));
        this.adapter = new DefaultProductServiceAdapter(productCurator, productCertificateCurator);
    }

    @Test
    public void productsByIds() {
        Owner owner = TestUtil.createOwner("test_owner");

        Map<String, Product> result = Map.of();
        when(productCurator.resolveProductIds(nullable(String.class), anyCollection())).thenReturn(result);
        List<String> ids = List.of(SOME_ID);

        adapter.getProductsByIds(owner.getKey(), ids);

        verify(productCurator).resolveProductIds(owner.getKey(), ids);
    }

    @Test
    public void productCertificateExists() {
        Owner owner = TestUtil.createOwner("test_owner");
        Product product = TestUtil.createProduct("test_product");
        ProductCertificate cert = mock(ProductCertificate.class);

        when(productCurator.resolveProductId(owner.getKey(), product.getId())).thenReturn(product);
        doReturn(cert).when(productCertificateCurator).findForProduct(product);

        CertificateInfo result = adapter.getProductCertificate(owner.getKey(), product.getId());
        verify(productCertificateCurator, never()).create(cert);

        assertEquals(cert, result);
    }

    @Test
    public void productCertificateNew() throws Exception {
        Owner owner = TestUtil.createOwner("test_owner");
        Product product = TestUtil.createProduct("123");
        ProductCertificate cert = mock(ProductCertificate.class);

        when(productCurator.resolveProductId(owner.getKey(), product.getId())).thenReturn(product);
        doAnswer(returnsFirstArg()).when(productCertificateCurator).create(any(ProductCertificate.class));
        doReturn(null).when(productCertificateCurator).findForProduct(product);

        KeyPair kp = createKeyPair();
        when(keyPairGenerator.generateKeyPair()).thenReturn(kp);
        when(pki.getPemEncoded(any(PrivateKey.class))).thenReturn("junk".getBytes());

        CertificateInfo result = adapter.getProductCertificate(owner.getKey(), product.getId());
        assertNotNull(result);
    }

    @Test
    public void testGetChildrenByProductIdsSkuIds() {
        List<ProductInfo> actual = adapter.getChildrenByProductIds(List.of("sku-id"));

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    // can't mock a final class, so create a dummy one
    private KeyPair createKeyPair() {
        PublicKey pk = mock(PublicKey.class);
        PrivateKey ppk = mock(PrivateKey.class);
        return new KeyPair(pk, ppk);
    }
}
