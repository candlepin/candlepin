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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ResultIterator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.test.TestUtil;
import org.candlepin.util.X509ExtensionUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;


// TODO: FIXME: Rewrite this test to not be so reliant upon mocks. It's making things incredibly brittle and
// wasting dev time tracking down non-issues when a mock silently fails because the implementation changes.
public class DefaultProductServiceAdapterTest {
    private String someid = "deadbeef";
    private DefaultProductServiceAdapter dpsa;
    private OwnerProductCurator opc;
    private ProductCertificateCurator pcc;
    private UniqueIdGenerator idgen;
    private PKIUtility pki;
    private X509ExtensionUtil extUtil;
    private ContentCurator cc;

    @BeforeEach
    public void init() {
        opc = mock(OwnerProductCurator.class);
        idgen = mock(UniqueIdGenerator.class);
        Configuration config = mock(Configuration.class);
        pki = mock(PKIUtility.class);
        extUtil = new X509ExtensionUtil(config);
        cc = mock(ContentCurator.class);
        pcc = spy(new ProductCertificateCurator(pki, extUtil));
        when(config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING)).thenReturn(false);
        dpsa = new DefaultProductServiceAdapter(opc, pcc, cc, idgen);
    }

    @Test
    public void productsByIds() {
        Owner o = mock(Owner.class);
        List<String> ids = new ArrayList<>();
        CandlepinQuery<Product> ccmock = mock(CandlepinQuery.class);
        ResultIterator<Product> iterator = mock(ResultIterator.class);

        when(opc.getProductsByIdsUsingOwnerKey(nullable(String.class), anyCollection())).thenReturn(ccmock);
        when(ccmock.iterate(anyInt(), anyBoolean())).thenReturn(iterator);

        ids.add(someid);

        dpsa.getProductsByIds(o.getKey(), ids);
        verify(opc).getProductsByIdsUsingOwnerKey(eq(o.getKey()), eq(ids));
    }

    @Test
    public void productCertificateExists() {
        Owner owner = TestUtil.createOwner("test_owner");
        Product product = TestUtil.createProduct("test_product");
        ProductCertificate cert = mock(ProductCertificate.class);

        when(opc.getProductByIdUsingOwnerKey(eq(owner.getKey()), eq(product.getId()))).thenReturn(product);
        doReturn(cert).when(pcc).findForProduct(eq(product));

        CertificateInfo result = dpsa.getProductCertificate(owner.getKey(), product.getId());
        verify(pcc, never()).create(eq(cert));

        assertEquals(cert, result);
    }

    @Test
    public void productCertificateNew() throws Exception {
        Owner owner = TestUtil.createOwner("test_owner");
        Product product = TestUtil.createProduct("test_product");
        ProductCertificate cert = mock(ProductCertificate.class);

        when(opc.getProductByIdUsingOwnerKey(eq(owner.getKey()), eq(product.getId()))).thenReturn(product);
        doAnswer(returnsFirstArg()).when(pcc).create(any(ProductCertificate.class));
        doReturn(null).when(pcc).findForProduct(eq(product));

        KeyPair kp = createKeyPair();
        when(pki.generateKeyPair()).thenReturn(kp);
        when(pki.getPemEncoded(any(PrivateKey.class))).thenReturn("junk".getBytes());

        CertificateInfo result = dpsa.getProductCertificate(owner.getKey(), product.getId());
        assertNotNull(result);
    }

    // can't mock a final class, so create a dummy one
    private KeyPair createKeyPair() {
        PublicKey pk = mock(PublicKey.class);
        PrivateKey ppk = mock(PrivateKey.class);
        return new KeyPair(pk, ppk);
    }
}
