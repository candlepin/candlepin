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

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.shim.ContentDataTranslator;
import org.candlepin.dto.shim.ProductDataTranslator;
import org.candlepin.dto.shim.ProductDataTranslatorTest;
import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Owner;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.util.Util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;


/**
 * Test suite for the ProductTranslator class
 */
public class SubscriptionTranslatorTest extends
    AbstractTranslatorTest<Subscription, SubscriptionDTO, SubscriptionTranslator> {

    private SubscriptionTranslator translator = new SubscriptionTranslator();
    private CdnTranslatorTest cdnTest = new CdnTranslatorTest();
    private NestedOwnerTranslatorTest ownerTest = new NestedOwnerTranslatorTest();
    private ProductDataTranslatorTest productTest = new ProductDataTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(new BrandingTranslator(), Branding.class, BrandingDTO.class);
        modelTranslator.registerTranslator(new CdnTranslator(), Cdn.class, CdnDTO.class);
        modelTranslator.registerTranslator(
            new CertificateSerialTranslator(), CertificateSerial.class, CertificateSerialDTO.class);
        modelTranslator.registerTranslator(new ContentDataTranslator(), ContentData.class, ContentDTO.class);
        modelTranslator.registerTranslator(
            new CertificateTranslator(), Certificate.class, CertificateDTO.class);
        modelTranslator.registerTranslator(new NestedOwnerTranslator(), Owner.class, NestedOwnerDTO.class);
        modelTranslator.registerTranslator(
            new ProductDataTranslator(), ProductData.class, ProductDTO.class);
        modelTranslator.registerTranslator(this.translator, Subscription.class, SubscriptionDTO.class);
    }

    @Override
    protected SubscriptionTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Subscription initSourceObject() {
        Subscription source = new Subscription();

        source.setId("test_id");
        source.setOwner(this.ownerTest.initSourceObject());
        source.setProduct(this.productTest.initSourceObject());
        source.setQuantity(15L);
        source.setStartDate(new Date());
        source.setEndDate(new Date());
        source.setContractNumber("test_contact");
        source.setAccountNumber("test_acc_num");
        source.setModified(new Date());
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

    private Collection<ProductData> createProducts() {
        return Arrays.asList(
            this.productTest.initSourceObject(),
            this.productTest.initSourceObject()
        );
    }

    @Override
    protected SubscriptionDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new SubscriptionDTO();
    }

    @Override
    protected void verifyOutput(
        Subscription source, SubscriptionDTO dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getQuantity(), dto.getQuantity());
            assertEquals(Util.toDateTime(source.getStartDate()), dto.getStartDate());
            assertEquals(Util.toDateTime(source.getEndDate()), dto.getEndDate());
            assertEquals(source.getContractNumber(), dto.getContractNumber());
            assertEquals(source.getAccountNumber(), dto.getAccountNumber());
            assertEquals(Util.toDateTime(source.getModified()), dto.getModified());
            assertEquals(Util.toDateTime(source.getLastModified()), dto.getLastModified());
            assertEquals(source.getOrderNumber(), dto.getOrderNumber());
            assertEquals(source.getUpstreamPoolId(), dto.getUpstreamPoolId());
            assertEquals(source.getUpstreamEntitlementId(), dto.getUpstreamEntitlementId());
            assertEquals(source.getUpstreamConsumerId(), dto.getUpstreamConsumerId());
            assertEquals(source.isStacked(), dto.getStacked());
            assertEquals(source.getStackId(), dto.getStackId());

            if (childrenGenerated) {
                assertNotNull(source.getCertificate());
                this.ownerTest.verifyOutput(source.getOwner(), dto.getOwner(), childrenGenerated);
                this.productTest.verifyOutput(source.getProduct(), dto.getProduct(), childrenGenerated);
                this.productTest.verifyOutput(
                    source.getDerivedProduct(), dto.getDerivedProduct(), childrenGenerated);
                this.cdnTest.verifyOutput(source.getCdn(), dto.getCdn(), childrenGenerated);

                for (ProductData product : source.getProvidedProducts()) {
                    assertNotNull(product);
                }

                for (ProductData product : source.getDerivedProvidedProducts()) {
                    assertNotNull(product);
                }
            }
            else {
                assertTrue(isEmpty(dto.getProvidedProducts()));
                assertTrue(isEmpty(dto.getDerivedProvidedProducts()));
                assertNull(dto.getOwner());
                assertNull(dto.getProduct());
                assertNull(dto.getDerivedProduct());
                assertNull(dto.getCert());
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

}
