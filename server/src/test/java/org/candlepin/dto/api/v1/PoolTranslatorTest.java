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


import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.SubscriptionsCertificate;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test suite for the PoolTranslator class.
 */
public class PoolTranslatorTest extends AbstractTranslatorTest<Pool, PoolDTO, PoolTranslator> {

    protected PoolTranslator translator = new PoolTranslator();

    // Using EntitlementTranslator instead of EntitlementTranslatorTest to avoid StackOverflow issues
    // caused by bidirectional reference between Pool and Entitlement.
    private EntitlementTranslator entitlementTranslator = new EntitlementTranslator();

    private OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();
    private ProductTranslatorTest productTranslatorTest = new ProductTranslatorTest();
    private BrandingTranslatorTest brandingTranslatorTest = new BrandingTranslatorTest();
    private CertificateTranslatorTest certificateTranslatorTest = new CertificateTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);
        this.productTranslatorTest.initModelTranslator(modelTranslator);
        this.brandingTranslatorTest.initModelTranslator(modelTranslator);
        this.certificateTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, Pool.class, PoolDTO.class);
        modelTranslator.registerTranslator(this.entitlementTranslator,
            Entitlement.class, EntitlementDTO.class);
    }

    @Override
    protected PoolTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Pool initSourceObject() {
        Pool source = new Pool();
        source.setId("pool-id");

        source.setOwner(this.ownerTranslatorTest.initSourceObject());
        source.setProduct(this.productTranslatorTest.initSourceObject());
        source.setDerivedProduct(this.productTranslatorTest.initSourceObject());

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
        source.setCreatedByShare(false);
        source.setHasSharedAncestor(true);

        source.setQuantity(1L);
        source.setStartDate(new Date());
        source.setEndDate(new Date());

        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(Pool.Attributes.SOURCE_POOL_ID, "true");
        source.setAttributes(attributes);

        source.setRestrictedToUsername("restricted-to-username-value");
        source.setContractNumber("333");
        source.setAccountNumber("444");
        source.setOrderNumber("555");
        source.setConsumed(6L);
        source.setExported(7L);
        source.setShared(8L);

        Branding branding = new Branding();
        branding.setId("branding-id-1");
        branding.setName("branding-name-1");
        branding.setProductId("branding-prod-id-1");
        branding.setType("branding-type-1");
        Set<Branding> brandingSet = new HashSet<Branding>();
        brandingSet.add(branding);
        source.setBranding(brandingSet);

        Map<String, String> calculatedAttributes = new HashMap<String, String>();
        calculatedAttributes.put("calc-attribute-key-3", "calc-attribute-value-3");
        calculatedAttributes.put("calc-attribute-key-4", "calc-attribute-value-4");
        source.setCalculatedAttributes(calculatedAttributes);

        source.setUpstreamPoolId("upstream-pool-id-2");
        source.setUpstreamEntitlementId("upstream-entitlement-id-2");
        source.setUpstreamConsumerId("upstream-consumer-id-2");

        source.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");

        Product derivedProduct = new Product();
        derivedProduct.setId("derived-product-id-2");
        derivedProduct.setName("derived-product-name-2");
        derivedProduct.setAttributes(new HashMap<String, String>());
        derivedProduct.setAttribute(Product.Attributes.ARCHITECTURE, "POWER");
        derivedProduct.setAttribute(Product.Attributes.STACKING_ID, "2221");
        source.setDerivedProduct(derivedProduct);

        ProvidedProduct providedProd = new ProvidedProduct();
        providedProd.setProductId("provided-product-id-1");
        providedProd.setProductName("provided-product-name-1");
        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        providedProducts.add(providedProd);
        source.setProvidedProductDtos(providedProducts);

        ProvidedProduct derivedProvidedProd = new ProvidedProduct();
        derivedProvidedProd.setProductId("derived-provided-product-id-1");
        derivedProvidedProd.setProductName("derived-provided-product-name-1");
        Set<ProvidedProduct> derivedProvidedProducts = new HashSet<ProvidedProduct>();
        derivedProvidedProducts.add(derivedProvidedProd);
        source.setDerivedProvidedProductDtos(derivedProvidedProducts);

        Consumer sourceConsumer = new Consumer();
        sourceConsumer.setUuid("source-consumer-uuid");

        SourceStack sourceStack = new SourceStack();
        sourceStack.setSourceStackId("source-stack-source-stack-id-1");
        sourceStack.setId("source-stack-id-1");
        sourceStack.setSourceConsumer(sourceConsumer);
        source.setSourceStack(sourceStack);

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
            assertEquals(source.getActiveSubscription(), dest.isActiveSubscription());
            assertEquals(source.isCreatedByShare(), dest.isCreatedByShare());
            assertEquals(source.hasSharedAncestor(), dest.hasSharedAncestor());
            assertEquals(source.getQuantity(), dest.getQuantity());
            assertEquals(source.getStartDate(), dest.getStartDate());
            assertEquals(source.getEndDate(), dest.getEndDate());
            assertEquals(source.getAttributes(), dest.getAttributes());
            assertEquals(source.getRestrictedToUsername(), dest.getRestrictedToUsername());
            assertEquals(source.getContractNumber(), dest.getContractNumber());
            assertEquals(source.getAccountNumber(), dest.getAccountNumber());
            assertEquals(source.getOrderNumber(), dest.getOrderNumber());
            assertEquals(source.getConsumed(), dest.getConsumed());
            assertEquals(source.getExported(), dest.getExported());
            assertEquals(source.getShared(), dest.getShared());
            assertEquals(source.getCalculatedAttributes(), dest.getCalculatedAttributes());
            assertEquals(source.getUpstreamPoolId(), dest.getUpstreamPoolId());
            assertEquals(source.getUpstreamEntitlementId(), dest.getUpstreamEntitlementId());
            assertEquals(source.getUpstreamConsumerId(), dest.getUpstreamConsumerId());
            assertEquals(source.getProductName(), dest.getProductName());
            assertEquals(source.getProductId(), dest.getProductId());
            assertEquals(source.getProductAttributes(), dest.getProductAttributes());
            assertEquals(source.getStackId(), dest.getStackId());
            assertEquals(source.isStacked(), dest.isStacked());
            assertEquals(source.isDevelopmentPool(), dest.isDevelopmentPool());
            assertEquals(source.getDerivedProductAttributes(), dest.getDerivedProductAttributes());
            assertEquals(source.getDerivedProductId(), dest.getDerivedProductId());
            assertEquals(source.getDerivedProductName(), dest.getDerivedProductName());
            assertEquals(source.getSourceStackId(), dest.getSourceStackId());
            assertEquals(source.getSubscriptionSubKey(), dest.getSubscriptionSubKey());
            assertEquals(source.getSubscriptionId(), dest.getSubscriptionId());

            if (childrenGenerated) {
                this.ownerTranslatorTest
                    .verifyOutput(source.getOwner(), dest.getOwner(), true);

                this.certificateTranslatorTest
                    .verifyOutput(source.getCertificate(), dest.getCertificate(), true);

                Entitlement sourceSourceEntitlement = source.getSourceEntitlement();
                EntitlementDTO destSourceEntitlement = dest.getSourceEntitlement();
                if (sourceSourceEntitlement != null) {
                    assertEquals(sourceSourceEntitlement.getId(), destSourceEntitlement.getId());
                }
                else {
                    assertNull(destSourceEntitlement);
                }

                for (Branding brandingSource : source.getBranding()) {
                    for (BrandingDTO brandingDTO : dest.getBranding()) {

                        assertNotNull(brandingDTO);
                        assertNotNull(brandingDTO.getProductId());

                        if (brandingDTO.getProductId().equals(brandingSource.getProductId())) {
                            this.brandingTranslatorTest.verifyOutput(brandingSource, brandingDTO, true);
                        }
                    }
                }

                Set<Product> sourceProducts = source.getProvidedProducts();
                Set<PoolDTO.ProvidedProductDTO> productsDTO = dest.getProvidedProducts();
                verifyProductsOutput(sourceProducts, productsDTO);

                Set<Product> sourceDerivedProducts = source.getDerivedProvidedProducts();
                Set<PoolDTO.ProvidedProductDTO> derivedProductsDTO = dest.getDerivedProvidedProducts();
                verifyProductsOutput(sourceDerivedProducts, derivedProductsDTO);
            }
            else {
                assertNull(dest.getOwner());
                assertNull(dest.getSourceEntitlement());
                assertNull(dest.getBranding());
                assertNull(dest.getProvidedProducts());
                assertNull(dest.getDerivedProvidedProducts());
                assertNull(dest.getCertificate());
            }
        }
        else {
            assertNull(dest);
        }
    }

    /**
     * Verifies that the pool's sets of products are translated properly.
     *
     * @param originalProducts the original set or products we check against.
     *
     * @param dtoProducts the translated DTO set of products that we need to verify.
     */
    private static void verifyProductsOutput(Set<Product> originalProducts,
        Set<PoolDTO.ProvidedProductDTO> dtoProducts) {
        for (Product productSource : originalProducts) {
            for (PoolDTO.ProvidedProductDTO productDTO : dtoProducts) {

                assertNotNull(productDTO);
                assertNotNull(productDTO.getProductId());

                if (productDTO.getProductId().equals(productSource.getId())) {
                    assertTrue(productDTO.getProductName().equals(productSource.getName()));
                }
            }
        }
    }
}
