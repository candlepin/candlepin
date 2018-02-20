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
package org.candlepin.service.impl;

import static org.junit.Assert.*;
import static org.mockito.AdditionalAnswers.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.OwnerProductShareManager;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.test.TestUtil;
import org.candlepin.util.X509ExtensionUtil;

import org.junit.Before;
import org.junit.Test;

import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * DefaultProductServiceAdapterTest
 */
public class DefaultProductServiceAdapterTest {
    private String someid = "deadbeef";
    private DefaultProductServiceAdapter dpsa;
    private OwnerProductShareManager opsm;
    private ProductCertificateCurator pcc;
    private UniqueIdGenerator idgen;
    private PKIUtility pki;
    private X509ExtensionUtil extUtil;
    private ContentCurator cc;

    @Before
    public void init() {
        opsm = mock(OwnerProductShareManager.class);
        idgen = mock(UniqueIdGenerator.class);
        Configuration config = mock(Configuration.class);
        pki = mock(PKIUtility.class);
        extUtil = new X509ExtensionUtil(config);
        cc = mock(ContentCurator.class);
        pcc = spy(new ProductCertificateCurator(pki, extUtil));
        when(config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING)).thenReturn(false);
        dpsa = new DefaultProductServiceAdapter(opsm, pcc, cc, idgen);
    }

    @Test
    public void productsByIds() {
        Owner o = mock(Owner.class);
        Product p1 = mock(Product.class);
        Product p2 = mock(Product.class);
        List<Product> products = new ArrayList<Product>();
        products.add(p1);
        products.add(p2);
        when(opsm.resolveProductsByIds(any(Owner.class), anyCollection(), eq(true))).thenReturn(products);
        List<String> ids = new ArrayList<String>();
        ids.add(someid);
        dpsa.getProductsByIds(o, ids);
        verify(opsm).resolveProductsByIds(eq(o), eq(ids), eq(true));
        verify(p1).toDTO();
        verify(p2).toDTO();
    }

    @Test
    public void productCertificateExists() {
        Owner owner = TestUtil.createOwner("test_owner");
        Product product = TestUtil.createProduct("test_product");
        ProductCertificate cert = mock(ProductCertificate.class);

        when(opsm.resolveProductById(eq(owner), eq(product.getId()), eq(true))).thenReturn(product);
        doReturn(cert).when(pcc).findForProduct(eq(product));

        ProductCertificate result = dpsa.getProductCertificate(owner, product.getId());
        verify(pcc, never()).create(eq(cert));

        assertEquals(cert, result);
    }

    @Test
    public void productCertificateNew() throws Exception {
        Owner owner = TestUtil.createOwner("test_owner");
        Product product = TestUtil.createProduct("test_product");
        ProductCertificate cert = mock(ProductCertificate.class);

        when(opsm.resolveProductById(eq(owner), eq(product.getId()), eq(true))).thenReturn(product);
        doAnswer(returnsFirstArg()).when(pcc).create(any(ProductCertificate.class));
        doReturn(null).when(pcc).findForProduct(eq(product));

        KeyPair kp = createKeyPair();
        when(pki.generateNewKeyPair()).thenReturn(kp);
        when(pki.getPemEncoded(any(Key.class))).thenReturn("junk".getBytes());

        ProductCertificate result = dpsa.getProductCertificate(owner, product.getId());
        assertNotNull(result);
        assertEquals(product, result.getProduct());
    }

    // can't mock a final class, so create a dummy one
    private KeyPair createKeyPair() {
        PublicKey pk = mock(PublicKey.class);
        PrivateKey ppk = mock(PrivateKey.class);
        return new KeyPair(pk, ppk);
    }
}
