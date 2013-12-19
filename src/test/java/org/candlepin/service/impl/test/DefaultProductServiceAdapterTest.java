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
package org.candlepin.service.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.Config;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.impl.DefaultProductServiceAdapter;
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
    private UniqueIdGenerator idgen;
    private ProductCertificateCurator pcc;
    private PKIUtility pki;
    private X509ExtensionUtil extUtil;
    private ContentCurator cc;

    @Before
    public void init() {
        pc = mock(ProductCurator.class);
        idgen = mock(UniqueIdGenerator.class);
        pcc = mock(ProductCertificateCurator.class);
        pki = mock(PKIUtility.class);
        cc = mock(ContentCurator.class);
        Config config = mock(Config.class);
        when(config.environmentFilteringEnabled()).thenReturn(false);
        extUtil = new X509ExtensionUtil(config);
        dpsa = new DefaultProductServiceAdapter(pc, pcc, pki, extUtil, cc, idgen);
    }

    @Test
    public void productById() {
        // assert that the product returned by pc is unchanged
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(someid);
        when(pc.lookupById(eq(someid))).thenReturn(p);
        assertEquals(p, dpsa.getProductById(someid));
    }

    @Test
    public void productsByIds() {
        List<String> ids = new ArrayList<String>();
        ids.add(someid);
        dpsa.getProductsByIds(ids);
        verify(pc).listAllByIds(eq(ids));
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
        when(pcc.findForProduct((eq(p)))).thenReturn(cert);
        dpsa.deleteProduct(p);
        verify(pcc).delete(eq(cert));
        verify(pc).delete(eq(p));
    }

    @Test
    public void deleteProductNoCerts() {
        Product p = mock(Product.class);
        when(pcc.findForProduct((eq(p)))).thenReturn(null);
        dpsa.deleteProduct(p);
        verify(pcc, never()).delete(any(ProductCertificate.class));
        verify(pc).delete(eq(p));
    }

    @Test
    public void productCertificateExists() {
        Product p = mock(Product.class);
        ProductCertificate cert = mock(ProductCertificate.class);
        when(pcc.findForProduct((eq(p)))).thenReturn(cert);
        ProductCertificate result = dpsa.getProductCertificate(p);
        verify(pcc, never()).create(eq(cert));
        assertEquals(cert, result);
    }

    @Test
    public void productCertificateNew() throws Exception {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(someid);
        when(pcc.findForProduct((eq(p)))).thenReturn(null);
        KeyPair kp = createKeyPair();
        when(pki.generateNewKeyPair()).thenReturn(kp);
        when(pki.getPemEncoded(any(Key.class))).thenReturn("junk".getBytes());
        ProductCertificate result = dpsa.getProductCertificate(p);
        assertNotNull(result);
        assertEquals(p, result.getProduct());
    }

    @Test
    public void removeContent() {
        Product p = mock(Product.class);
        Content c = mock(Content.class);
        when(pc.find(eq(someid))).thenReturn(p);
        when(cc.find(eq("cid"))).thenReturn(c);

        dpsa.removeContent(someid, "cid");

        verify(pc, atLeastOnce()).removeProductContent(eq(p), eq(c));
    }

    @Test
    public void hasSubscriptions() {
        Product p = mock(Product.class);
        dpsa.productHasSubscriptions(p);
        verify(pc).productHasSubscriptions(eq(p));
    }

    @Test
    public void addRely() {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(someid);
        when(pc.find(eq(someid))).thenReturn(p);
        dpsa.addRely(someid, "beefdead");
        verify(pc).addRely(eq(p), eq("beefdead"));
    }

    @Test
    public void reliesOn() {
        Product p = mock(Product.class);
        when(pc.find(eq(someid))).thenReturn(p);
        dpsa.getReliesOn(someid);
        verify(p).getReliesOn();
    }

    @Test
    public void removeRely() {
        Product p = mock(Product.class);
        when(pc.find(eq(someid))).thenReturn(p);
        dpsa.removeRely(someid, "relyid");
        verify(pc).removeRely(eq(p), eq("relyid"));
    }

    // can't mock a final class, so create a dummy one
    private KeyPair createKeyPair() {
        PublicKey pk = mock(PublicKey.class);
        PrivateKey ppk = mock(PrivateKey.class);
        return new KeyPair(pk, ppk);
    }
}
