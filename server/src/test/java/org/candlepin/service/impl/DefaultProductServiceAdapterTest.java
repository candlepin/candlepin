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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.service.UniqueIdGenerator;
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
    private ProductCurator pc;
    private ProductCertificateCurator pcc;
    private UniqueIdGenerator idgen;
    private PKIUtility pki;
    private X509ExtensionUtil extUtil;
    private ContentCurator cc;

    @Before
    public void init() {
        pc = mock(ProductCurator.class);
        idgen = mock(UniqueIdGenerator.class);
        Configuration config = mock(Configuration.class);
        pki = mock(PKIUtility.class);
        extUtil = new X509ExtensionUtil(config);
        cc = mock(ContentCurator.class);
        pcc = spy(new ProductCertificateCurator(pki, extUtil));
        when(config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING)).thenReturn(false);
        dpsa = new DefaultProductServiceAdapter(pc, pcc, cc, idgen);
    }

    @Test
    public void productById() {
        // assert that the product returned by pc is unchanged
        Owner o = mock(Owner.class);
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(someid);
        when(pc.lookupById(eq(o), eq(someid))).thenReturn(p);
        assertEquals(p, dpsa.getProductById(o, someid));
    }

    @Test
    public void productsByIds() {
        Owner o = mock(Owner.class);
        List<String> ids = new ArrayList<String>();
        ids.add(someid);
        dpsa.getProductsByIds(o, ids);
        verify(pc).listAllByIds(eq(o), eq(ids));
    }

    @Test
    public void getProducts() {
        List<Product> prods = new ArrayList<Product>();
        prods.add(mock(Product.class));
        prods.add(mock(Product.class));
        when(pc.listAll()).thenReturn(prods);
        List<Product> result = dpsa.getProducts();
        assertEquals(prods, result);
    }

    @Test(expected = BadRequestException.class)
    public void createExistingProduct() {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(someid);
        when(pc.find(someid)).thenReturn(p);
        dpsa.createProduct(p);
    }

    @Test
    public void createNewProduct() {
        Product p = mock(Product.class);
        when(pc.find(null)).thenReturn(null);
        when(pc.create(eq(p))).thenReturn(p);
        when(idgen.generateId()).thenReturn(someid);
        Product result = dpsa.createProduct(p);
        verify(p).setId(eq(someid));
        assertNotNull(result);
    }

    @Test
    public void deleteProductWithCerts() {
        Product p = mock(Product.class);
        ProductCertificate cert = mock(ProductCertificate.class);
        doNothing().when(pcc).delete(any(ProductCertificate.class));
        doReturn(cert).when(pcc).findForProduct(eq(p));
        dpsa.deleteProduct(p);
        verify(pcc).delete(eq(cert));
        verify(pc).delete(eq(p));
    }

    @Test
    public void deleteProductNoCerts() {
        Product p = mock(Product.class);
        doReturn(null).when(pcc).findForProduct(eq(p));
        dpsa.deleteProduct(p);
        verify(pcc, never()).delete(any(ProductCertificate.class));
        verify(pc).delete(eq(p));
    }

    @Test
    public void productCertificateExists() {
        Product p = mock(Product.class);
        ProductCertificate cert = mock(ProductCertificate.class);
        doReturn(cert).when(pcc).findForProduct(eq(p));
        ProductCertificate result = dpsa.getProductCertificate(p);
        verify(pcc, never()).create(eq(cert));
        assertEquals(cert, result);
    }

    @Test
    public void productCertificateNew() throws Exception {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(someid);
        doAnswer(returnsFirstArg()).when(pcc).create(any(ProductCertificate.class));
        doReturn(null).when(pcc).findForProduct(eq(p));
        KeyPair kp = createKeyPair();
        when(pki.generateNewKeyPair()).thenReturn(kp);
        when(pki.getPemEncoded(any(Key.class))).thenReturn("junk".getBytes());
        ProductCertificate result = dpsa.getProductCertificate(p);
        assertNotNull(result);
        assertEquals(p, result.getProduct());
    }

    @Test
    public void removeContent() {
        Owner o = mock(Owner.class);
        Product p = mock(Product.class);
        Content c = mock(Content.class);
        when(pc.lookupById(eq(o), eq(someid))).thenReturn(p);
        when(cc.lookupById(eq(o), eq("cid"))).thenReturn(c);

        dpsa.removeContent(o, someid, "cid");

        verify(pc, atLeastOnce()).removeProductContent(eq(p), eq(c));
    }

    @Test
    public void hasSubscriptions() {
        Product p = mock(Product.class);
        dpsa.productHasSubscriptions(p);
        verify(pc).productHasSubscriptions(eq(p));
    }

    // can't mock a final class, so create a dummy one
    private KeyPair createKeyPair() {
        PublicKey pk = mock(PublicKey.class);
        PrivateKey ppk = mock(PrivateKey.class);
        return new KeyPair(pk, ppk);
    }
}
