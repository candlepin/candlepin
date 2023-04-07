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
package org.candlepin.dto.shim;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.CdnDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.dto.api.server.v1.SubscriptionDTO;
import org.candlepin.dto.api.v1.CdnTranslator;
import org.candlepin.dto.api.v1.NestedOwnerTranslator;
import org.candlepin.model.Cdn;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Owner;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.Util;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


public class SubscriptionInfoTranslatorTest extends AbstractTranslatorTest<SubscriptionInfo, SubscriptionDTO,
    SubscriptionInfoTranslator> {

    protected SubscriptionInfoTranslator translator = new SubscriptionInfoTranslator();

    private final ProductInfoTranslatorTest productTranslatorTest = new ProductInfoTranslatorTest();
    private final BrandingInfoTranslatorTest brandingTranslatorTest = new BrandingInfoTranslatorTest();
    private final CertificateInfoTranslatorTest certificateTranslatorTest =
        new CertificateInfoTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.productTranslatorTest.initModelTranslator(modelTranslator);
        this.brandingTranslatorTest.initModelTranslator(modelTranslator);
        this.certificateTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, SubscriptionInfo.class, SubscriptionDTO.class);
        modelTranslator.registerTranslator(new NestedOwnerTranslator(), Owner.class, NestedOwnerDTO.class);
        modelTranslator.registerTranslator(new ProductInfoTranslator(), ProductInfo.class, ProductDTO.class);
        modelTranslator.registerTranslator(new ContentInfoTranslator(), ContentInfo.class, ContentDTO.class);
        modelTranslator.registerTranslator(new CdnTranslator(), Cdn.class, CdnDTO.class);
    }

    @Override
    protected SubscriptionInfoTranslator initObjectTranslator() {
        return this.translator;
    }

    private ProductData generateProduct(String prefix, int id) {
        return new ProductData()
            .setId(prefix + "-id-" + id)
            .setName(prefix + "-name-" + id)
            .setAttribute(prefix + "-attrib" + id + "-key", prefix + "-attrb" + id + "-value")
            .setAttribute(prefix + "-attrib" + id + "-key", prefix + "-attrb" + id + "-value")
            .setAttribute(prefix + "-attrib" + id + "-key", prefix + "-attrb" + id + "-value");
    }

    @Override
    protected SubscriptionInfo initSourceObject() {
        Subscription source = new Subscription();
        source.setId("pool-id");
        source.setCreated(Util.toDate(OffsetDateTime.now().minusMinutes(10)));
        source.setUpdated(new Date());
        source.setModified(new Date());
        source.setStartDate(new Date());
        source.setEndDate(Util.toDate(OffsetDateTime.now().plusYears(10)));
        source.setAccountNumber("444");
        source.setContractNumber("333");
        source.setOrderNumber("555");
        source.setQuantity(1L);
        source.setUpstreamPoolId("upstream-pool-id-2");
        source.setUpstreamEntitlementId("upstream-entitlement-id-2");
        source.setUpstreamConsumerId("upstream-consumer-id-2");

        source.setOwner(new Owner()
            .setId("owner_id")
            .setKey("owner_key")
            .setDisplayName("owner_name"));
        source.setCdn(new Cdn("cdn_label", "cdn_name", "cdn_url"));

        SubscriptionsCertificate subCert = new SubscriptionsCertificate();
        subCert.setId("cert-id");
        subCert.setKey("cert-key");
        subCert.setCert("cert-cert");
        subCert.setSerial(new CertificateSerial());
        source.setCertificate(subCert);

        ProductData mktProduct = this.generateProduct("mkt_product", 1);
        ProductData engProduct1 = this.generateProduct("eng_product", 1);
        ProductData engProduct2 = this.generateProduct("eng_product", 2);
        ProductData engProduct3 = this.generateProduct("eng_product", 3);

        ProductData derProduct = this.generateProduct("derived_product", 1);
        ProductData derEngProduct1 = this.generateProduct("derived_eng_prod", 1);
        ProductData derEngProduct2 = this.generateProduct("derived_eng_prod", 2);
        ProductData derEngProduct3 = this.generateProduct("derived_eng_prod", 3);

        mktProduct.setDerivedProduct(derProduct);
        mktProduct.setProvidedProducts(Arrays.asList(engProduct1, engProduct2, engProduct3));
        derProduct.setProvidedProducts(Arrays.asList(derEngProduct1, derEngProduct2, derEngProduct3));
        source.setProduct(mktProduct);

        return source;
    }

    @Override
    protected SubscriptionDTO initDestinationObject() {
        return new SubscriptionDTO();
    }

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    protected void verifyOutput(SubscriptionInfo source, SubscriptionDTO dest, boolean childrenGenerated) {
        if (source == null) {
            assertNull(dest);
            return;
        }

        assertEquals(source.getId(), dest.getId());
        assertEquals(source.getLastModified(), Util.toDate(dest.getLastModified()));
        assertEquals(source.getStartDate(), Util.toDate(dest.getStartDate()));
        assertEquals(source.getEndDate(), Util.toDate(dest.getEndDate()));
        assertEquals(source.getAccountNumber(), dest.getAccountNumber());
        assertEquals(source.getContractNumber(), dest.getContractNumber());
        assertEquals(source.getOrderNumber(), dest.getOrderNumber());
        assertEquals(source.getQuantity(), dest.getQuantity());
        assertEquals(source.getUpstreamConsumerId(), dest.getUpstreamConsumerId());
        assertEquals(source.getUpstreamEntitlementId(), dest.getUpstreamEntitlementId());
        assertEquals(source.getUpstreamPoolId(), dest.getUpstreamPoolId());

        // Validate other children that require the model translator
        if (!childrenGenerated) {
            assertNull(dest.getCdn());
            assertNull(dest.getCertificate());
            assertNull(dest.getOwner());
            assertNull(dest.getProduct());
            assertNull(dest.getDerivedProduct());
            assertTrue(dest.getProvidedProducts().isEmpty());
            assertTrue(dest.getDerivedProvidedProducts().isEmpty());
        }
        else {
            this.certificateTranslatorTest
                .verifyOutput(source.getCertificate(), dest.getCertificate(), true);

            // Check data originating from the product
            ProductInfo srcProduct = source.getProduct();
            ProductInfo srcDerived = null;

            if (srcProduct != null) {
                assertEquals(srcProduct.getId(), dest.getProduct().getId());
                assertEquals(srcProduct.getName(), dest.getProduct().getName());
                assertEquals(srcProduct.getAttributes(), Util.toMap(dest.getProduct().getAttributes()));

                verifyProductCollection(srcProduct.getProvidedProducts(), dest.getProvidedProducts());

                srcDerived = srcProduct.getDerivedProduct();
            }
            else {
                assertNull(dest.getProduct().getId());
                assertNull(dest.getProduct().getName());
                assertNull(dest.getProduct().getAttributes());

                verifyProductCollection(null, dest.getProvidedProducts());
            }

            if (srcDerived != null) {
                assertEquals(srcDerived.getId(), dest.getDerivedProduct().getId());
                assertEquals(srcDerived.getName(), dest.getDerivedProduct().getName());
                assertEquals(srcDerived.getAttributes(),
                    Util.toMap(dest.getDerivedProduct().getAttributes()));

                verifyProductCollection(srcDerived.getProvidedProducts(), dest.getDerivedProvidedProducts());
            }
            else {
                assertNull(dest.getDerivedProduct().getId());
                assertNull(dest.getDerivedProduct().getName());
                assertNull(dest.getDerivedProduct().getAttributes());

                verifyProductCollection(null, dest.getDerivedProvidedProducts());
            }

        }
    }

    /**
     * Verifies that collections of products have been properly translated into collections of DTOs.
     *
     * @param source
     *  the source collection of untranslated products
     *
     * @param output
     *  the output collection of translated products
     */
    private static void verifyProductCollection(Collection<? extends ProductInfo> source,
        Collection<ProductDTO> output) {
        if (source == null) {
            assertNull(output);
            return;
        }

        Map<String, ProductInfo> srcProductMap = source.stream()
            .collect(Collectors.toMap(ProductInfo::getId, Function.identity()));

        Map<String, ProductDTO> outProductMap = output.stream()
            .collect(Collectors.toMap(ProductDTO::getId, Function.identity()));

        assertEquals(srcProductMap.keySet(), outProductMap.keySet());

        for (String id : srcProductMap.keySet()) {
            ProductInfo srcProduct = srcProductMap.get(id);
            ProductDTO outProduct = outProductMap.get(id);

            assertNotNull(srcProduct);
            assertNotNull(outProduct);

            assertEquals(srcProduct.getId(), outProduct.getId());
            assertEquals(srcProduct.getName(), outProduct.getName());
        }
    }
}
