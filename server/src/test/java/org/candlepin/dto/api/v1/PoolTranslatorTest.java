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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.util.Util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;



/**
 * Test suite for the PoolTranslator class.
 */
public class PoolTranslatorTest extends AbstractTranslatorTest<Pool, PoolDTO, PoolTranslator> {

    protected PoolTranslator translator = new PoolTranslator();

    private OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();
    private NestedOwnerTranslatorTest nestedOwnerTranslatorTest = new NestedOwnerTranslatorTest();
    private ProductTranslatorTest productTranslatorTest = new ProductTranslatorTest();
    private BrandingTranslatorTest brandingTranslatorTest = new BrandingTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);
        this.productTranslatorTest.initModelTranslator(modelTranslator);
        this.brandingTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, Pool.class, PoolDTO.class);
    }

    @Override
    protected PoolTranslator initObjectTranslator() {
        return this.translator;
    }

    private Product generateProduct(String prefix, int id) {
        return new Product()
            .setId(prefix + "-id-" + id)
            .setName(prefix + "-name-" + id)
            .setAttribute(prefix + "-attrib" + id + "-key", prefix + "-attrb" + id + "-value")
            .setAttribute(prefix + "-attrib" + id + "-key", prefix + "-attrb" + id + "-value")
            .setAttribute(prefix + "-attrib" + id + "-key", prefix + "-attrb" + id + "-value");
    }

    @Override
    protected Pool initSourceObject() {
        Pool source = new Pool();
        source.setId("pool-id");

        source.setOwner(this.ownerTranslatorTest.initSourceObject());

        Product mktProduct = this.generateProduct("mkt_product", 1);
        Product engProduct1 = this.generateProduct("eng_product", 1);
        Product engProduct2 = this.generateProduct("eng_product", 2);
        Product engProduct3 = this.generateProduct("eng_product", 3);

        Product derProduct = this.generateProduct("derived_product", 1);
        Product derEngProduct1 = this.generateProduct("derived_eng_prod", 1);
        Product derEngProduct2 = this.generateProduct("derived_eng_prod", 2);
        Product derEngProduct3 = this.generateProduct("derived_eng_prod", 3);

        mktProduct.setDerivedProduct(derProduct);
        mktProduct.setProvidedProducts(Arrays.asList(engProduct1, engProduct2, engProduct3));
        derProduct.setProvidedProducts(Arrays.asList(derEngProduct1, derEngProduct2, derEngProduct3));

        source.setProduct(mktProduct);

        Entitlement entitlement = new Entitlement();
        entitlement.setId("ent-id");
        source.setSourceEntitlement(entitlement);

        SubscriptionsCertificate subCert = new SubscriptionsCertificate();
        subCert.setId("cert-id");
        subCert.setKey("cert-key");
        subCert.setCert("cert-cert");
        subCert.setSerial(new CertificateSerial());
        source.setCertificate(subCert);

        SourceSubscription sourceSubscription = new SourceSubscription();
        sourceSubscription.setId("source-sub-id-1");
        sourceSubscription.setSubscriptionId("source-sub-subscription-id-1");
        sourceSubscription.setSubscriptionSubKey("source-sub-subscription-sub-key-1");
        source.setSourceSubscription(sourceSubscription);

        source.setActiveSubscription(true);

        source.setQuantity(1L);
        source.setStartDate(new Date());
        source.setEndDate(new Date());

        Map<String, String> attributes = new HashMap<>();
        attributes.put(Pool.Attributes.SOURCE_POOL_ID, "true");
        source.setAttributes(attributes);

        source.setRestrictedToUsername("restricted-to-username-value");
        source.setContractNumber("333");
        source.setAccountNumber("444");
        source.setOrderNumber("555");
        source.setConsumed(6L);
        source.setExported(7L);

        Map<String, String> calculatedAttributes = new HashMap<>();
        calculatedAttributes.put("calc-attribute-key-3", "calc-attribute-value-3");
        calculatedAttributes.put("calc-attribute-key-4", "calc-attribute-value-4");
        source.setCalculatedAttributes(calculatedAttributes);

        source.setUpstreamPoolId("upstream-pool-id-2");
        source.setUpstreamEntitlementId("upstream-entitlement-id-2");
        source.setUpstreamConsumerId("upstream-consumer-id-2");

        source.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");

        Consumer sourceConsumer = new Consumer();
        sourceConsumer.setUuid("source-consumer-uuid");

        SourceStack sourceStack = new SourceStack();
        sourceStack.setSourceStackId("source-stack-source-stack-id-1");
        sourceStack.setId("source-stack-id-1");
        sourceStack.setSourceConsumer(sourceConsumer);
        source.setSourceStack(sourceStack);
        source.setLocked(true);

        return source;
    }

    @Override
    protected PoolDTO initDestinationObject() {
        return new PoolDTO();
    }

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    protected void verifyOutput(Pool source, PoolDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getType().toString(), dest.getType());
            assertEquals(source.getActiveSubscription(), dest.getActiveSubscription());
            assertEquals(source.getQuantity(), dest.getQuantity());
            assertEquals(source.getStartDate(), Util.toDate(dest.getStartDate()));
            assertEquals(source.getEndDate(), Util.toDate(dest.getEndDate()));
            assertEquals(source.getAttributes(), Util.toMap(dest.getAttributes()));
            assertEquals(source.getRestrictedToUsername(), dest.getRestrictedToUsername());
            assertEquals(source.getContractNumber(), dest.getContractNumber());
            assertEquals(source.getAccountNumber(), dest.getAccountNumber());
            assertEquals(source.getOrderNumber(), dest.getOrderNumber());
            assertEquals(source.getConsumed(), dest.getConsumed());
            assertEquals(source.getExported(), dest.getExported());
            assertEquals(source.getCalculatedAttributes(), dest.getCalculatedAttributes());
            assertEquals(source.getUpstreamPoolId(), dest.getUpstreamPoolId());
            assertEquals(source.getUpstreamEntitlementId(), dest.getUpstreamEntitlementId());
            assertEquals(source.getUpstreamConsumerId(), dest.getUpstreamConsumerId());
            assertEquals(source.getStackId(), dest.getStackId());
            assertEquals(source.isStacked(), dest.getStacked());
            assertEquals(source.isDevelopmentPool(), dest.getDevelopmentPool());
            assertEquals(source.getSourceStackId(), dest.getSourceStackId());
            assertEquals(source.getSubscriptionSubKey(), dest.getSubscriptionSubKey());
            assertEquals(source.getSubscriptionId(), dest.getSubscriptionId());
            assertEquals(source.isLocked(), dest.getLocked());

            // Check data originating from the product
            Product srcProduct = source.getProduct();
            Product srcDerived = null;

            if (srcProduct != null) {
                assertEquals(srcProduct.getId(), dest.getProductId());
                assertEquals(srcProduct.getName(), dest.getProductName());
                assertEquals(srcProduct.getAttributes(), dest.getProductAttributes());

                verifyProductCollection(srcProduct.getProvidedProducts(), dest.getProvidedProducts());

                srcDerived = srcProduct.getDerivedProduct();
            }
            else {
                assertNull(dest.getProductId());
                assertNull(dest.getProductName());
                assertNull(dest.getProductAttributes());

                verifyProductCollection(null, dest.getProvidedProducts());
            }

            if (srcDerived != null) {
                assertEquals(srcDerived.getId(), dest.getDerivedProductId());
                assertEquals(srcDerived.getName(), dest.getDerivedProductName());
                assertEquals(srcDerived.getAttributes(), dest.getDerivedProductAttributes());

                verifyProductCollection(srcDerived.getProvidedProducts(), dest.getDerivedProvidedProducts());
            }
            else {
                assertNull(dest.getDerivedProductId());
                assertNull(dest.getDerivedProductName());
                assertNull(dest.getDerivedProductAttributes());

                verifyProductCollection(null, dest.getDerivedProvidedProducts());
            }

            // Validate other children that require the model translator
            if (childrenGenerated) {
                this.nestedOwnerTranslatorTest
                    .verifyOutput(source.getOwner(), dest.getOwner(), true);

                // Source entitlements
                Entitlement srcSourceEntitlement = source.getSourceEntitlement();
                if (srcSourceEntitlement != null) {
                    assertNotNull(dest.getSourceEntitlement());
                    assertEquals(srcSourceEntitlement.getId(), dest.getSourceEntitlement().getId());
                }
                else {
                    assertNull(dest.getSourceEntitlement());
                }

                // Check branding
                if (srcProduct != null && srcProduct.getBranding() != null) {
                    int matched = 0;

                    for (Branding brandingSource : srcProduct.getBranding()) {
                        for (BrandingDTO brandingDTO : dest.getBranding()) {

                            assertNotNull(brandingDTO);
                            assertNotNull(brandingDTO.getProductId());

                            if (brandingDTO.getProductId().equals(brandingSource.getProductId())) {
                                this.brandingTranslatorTest.verifyOutput(brandingSource, brandingDTO, true);
                                ++matched;
                            }
                        }
                    }

                    assertEquals(srcProduct.getBranding().size(), matched);
                }
                else {
                    assertNull(dest.getBranding());
                }
            }
            else {
                assertNull(dest.getOwner());
                assertNull(dest.getSourceEntitlement());
                assertNull(dest.getBranding());
            }
        }
        else {
            assertNull(dest);
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
    private static void verifyProductCollection(Collection<Product> source,
        Collection<ProvidedProductDTO> output) {

        if (source != null) {
            Map<String, Product> srcProductMap = source.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

            Map<String, ProvidedProductDTO> outProductMap = output.stream()
                .collect(Collectors.toMap(ProvidedProductDTO::getProductId, Function.identity()));

            assertEquals(srcProductMap.keySet(), outProductMap.keySet());

            for (String id : srcProductMap.keySet()) {
                Product srcProduct = srcProductMap.get(id);
                ProvidedProductDTO outProduct = outProductMap.get(id);

                assertNotNull(srcProduct);
                assertNotNull(outProduct);

                assertEquals(srcProduct.getId(), outProduct.getProductId());
                assertEquals(srcProduct.getName(), outProduct.getProductName());
            }
        }
        else {
            assertNull(output);
        }
    }
}
