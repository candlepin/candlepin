/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.shim.ContentDataTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.model.dto.ContentData;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.Test;

import java.util.Date;


/**
 * Test suite for the PoolToSubscriptionTranslatorTest class
 */
public class PoolToSubscriptionTranslatorTest extends
    AbstractTranslatorTest<Pool, SubscriptionDTO, PoolToSubscriptionTranslator> {

    ProductCurator productCurator;
    private PoolToSubscriptionTranslator translator;
    private CdnTranslatorTest cdnTest = new CdnTranslatorTest();
    private NestedOwnerTranslatorTest ownerTest = new NestedOwnerTranslatorTest();
    private ProductTranslatorTest productTest = new ProductTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(new BrandingTranslator(), Branding.class, BrandingDTO.class);
        modelTranslator.registerTranslator(new CdnTranslator(), Cdn.class, CdnDTO.class);
        modelTranslator.registerTranslator(
            new CertificateSerialTranslator(), CertificateSerial.class, CertificateSerialDTO.class);
        modelTranslator.registerTranslator(new ContentDataTranslator(), ContentData.class, ContentDTO.class);
        modelTranslator.registerTranslator(
            new CertificateTranslator(), Certificate.class, CertificateDTO.class);
        modelTranslator.registerTranslator(
            new ContentTranslator(), Content.class, ContentDTO.class);
        modelTranslator.registerTranslator(new NestedOwnerTranslator(), Owner.class, NestedOwnerDTO.class);
        modelTranslator.registerTranslator(
            new ProductTranslator(), Product.class, ProductDTO.class);
        modelTranslator.registerTranslator(
            new ProductContentTranslator(), ProductContent.class, ProductContentDTO.class);
        modelTranslator.registerTranslator(this.translator, Pool.class, SubscriptionDTO.class);
    }

    @Override
    protected PoolToSubscriptionTranslator initObjectTranslator() {
        this.productCurator = mock(ProductCurator.class);
        this.translator = new PoolToSubscriptionTranslator();
        return this.translator;
    }

    @Override
    protected Pool initSourceObject() {
        Pool source = new Pool();

        source.setId("test_id");
        source.setSubscriptionId("sub_test_id");
        source.setOwner(this.ownerTest.initSourceObject());
        source.setProduct(this.productTest.initSourceObject());
        source.setQuantity(15L);
        source.setStartDate(new Date());
        source.setEndDate(new Date());
        source.setContractNumber("test_contact");
        source.setAccountNumber("test_acc_num");
        source.setUpdated(new Date());
        source.setCreated(new Date());
        source.setUpdated(new Date());
        source.setOrderNumber("test_order_num");
        source.setUpstreamPoolId("test_pool_id");
        source.setUpstreamEntitlementId("test_ent_id");
        source.setUpstreamConsumerId("test_cons_id");
        source.setCertificate(this.createCert());
        source.setCdn(this.cdnTest.initSourceObject());

        return source;
    }

    @Override
    protected SubscriptionDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new SubscriptionDTO();
    }

    @Override
    protected void verifyOutput(
        Pool source, SubscriptionDTO dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getSubscriptionId(), dto.getId());
            assertEquals(source.getQuantity(), dto.getQuantity());
            assertEquals(Util.toDateTime(source.getStartDate()), dto.getStartDate());
            assertEquals(Util.toDateTime(source.getEndDate()), dto.getEndDate());
            assertEquals(source.getContractNumber(), dto.getContractNumber());
            assertEquals(source.getAccountNumber(), dto.getAccountNumber());
            assertEquals(Util.toDateTime(source.getUpdated()), dto.getModified());
            assertEquals(Util.toDateTime(source.getLastModified()), dto.getLastModified());
            assertEquals(source.getOrderNumber(), dto.getOrderNumber());
            assertEquals(source.getUpstreamPoolId(), dto.getUpstreamPoolId());
            assertEquals(source.getUpstreamEntitlementId(), dto.getUpstreamEntitlementId());
            assertEquals(source.getUpstreamConsumerId(), dto.getUpstreamConsumerId());
            assertEquals(source.isStacked(), dto.getStacked());
            assertEquals(source.getStackId(), dto.getStackId());
            assertEquals(source.getCreated(), Util.toDate(dto.getCreated()));
            assertEquals(source.getUpdated(), Util.toDate(dto.getUpdated()));

            if (childrenGenerated) {
                assertNotNull(source.getCertificate());
                this.ownerTest.verifyOutput(source.getOwner(), dto.getOwner(), childrenGenerated);
                this.productTest.verifyOutput(source.getProduct(), dto.getProduct(), childrenGenerated);
                this.cdnTest.verifyOutput(source.getCdn(), dto.getCdn(), childrenGenerated);
            }
            else {
                assertTrue(isEmpty(dto.getProvidedProducts()));
                assertTrue(isEmpty(dto.getDerivedProvidedProducts()));
                assertNull(dto.getOwner());
                assertNull(dto.getProduct());
                assertNull(dto.getDerivedProduct());
                assertNull(dto.getCertificate());
                assertNull(dto.getCdn());
            }
        }
        else {
            assertNull(dto);
        }
    }

    private SubscriptionsCertificate createCert() {
        SubscriptionsCertificate cert = new SubscriptionsCertificate();
        cert.setCert("HELLO");
        cert.setKey("CERT");
        return cert;
    }

    @Test
    public void testPopulateSubWithMultiplier() {
        Product product = TestUtil.createProduct("product", "Product");

        Pool pool = mock(Pool.class);

        Long quantity = new Long(22);
        Long multiplier = new Long(2);
        product.setMultiplier(multiplier);

        when(pool.getQuantity()).thenReturn(quantity);
        when(pool.getProduct()).thenReturn(product);

        SubscriptionDTO subDTO = new SubscriptionDTO();
        this.translator.populate(pool, subDTO);

        assertEquals((Long) (quantity / multiplier), subDTO.getQuantity());
    }

    @Test
    public void testPopulateSubWithZeroInstanceMultiplier() {
        Product product = TestUtil.createProduct("product", "Product");

        Pool pool = mock(Pool.class);

        Long quantity = new Long(64);
        Long multiplier = new Long(2);

        product.setMultiplier(multiplier);
        product.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "0");

        when(pool.getQuantity()).thenReturn(quantity);
        when(pool.getProduct()).thenReturn(product);

        SubscriptionDTO subDTO = new SubscriptionDTO();
        this.translator.populate(pool, subDTO);

        assertEquals((Long) 32L, subDTO.getQuantity());
    }

    @Test
    public void testPopulateSubWithMultiplierAndInstanceMultiplier() {
        Product product = TestUtil.createProduct("product", "Product");

        Pool pool = mock(Pool.class);

        Long quantity = new Long(64);
        Long multiplier = new Long(2);

        product.setMultiplier(multiplier);
        product.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "4");

        when(pool.getQuantity()).thenReturn(quantity);
        when(pool.getProduct()).thenReturn(product);

        SubscriptionDTO subDTO = new SubscriptionDTO();
        this.translator.populate(pool, subDTO);

        assertEquals((Long) 8L, subDTO.getQuantity());
    }

}
