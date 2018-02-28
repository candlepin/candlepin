/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.manifest.v1;


import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test suite for the PoolTranslator (manifest import/export) class.
 */
public class PoolTranslatorTest extends AbstractTranslatorTest<Pool, PoolDTO, PoolTranslator> {

    protected PoolTranslator translator = new PoolTranslator();

    private BrandingTranslatorTest brandingTranslatorTest = new BrandingTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.brandingTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, Pool.class, PoolDTO.class);
    }

    @Override
    protected PoolTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Pool initSourceObject() {
        Pool source = new Pool();
        source.setId("pool-id");

        source.setContractNumber("333");
        source.setAccountNumber("444");
        source.setOrderNumber("555");

        Branding branding = new Branding();
        branding.setId("branding-id-1");
        branding.setName("branding-name-1");
        branding.setProductId("branding-prod-id-1");
        branding.setType("branding-type-1");
        Set<Branding> brandingSet = new HashSet<>();
        brandingSet.add(branding);
        source.setBranding(brandingSet);

        Product product = new Product();
        product.setId("product-id-2");
        source.setDerivedProduct(product);

        Product derivedProduct = new Product();
        derivedProduct.setId("derived-product-id-2");
        source.setDerivedProduct(derivedProduct);

        ProvidedProduct providedProd = new ProvidedProduct();
        providedProd.setProductId("provided-product-id-1");
        providedProd.setProductName("provided-product-name-1");
        Set<ProvidedProduct> providedProducts = new HashSet<>();
        providedProducts.add(providedProd);
        source.setProvidedProductDtos(providedProducts);

        ProvidedProduct derivedProvidedProd = new ProvidedProduct();
        derivedProvidedProd.setProductId("derived-provided-product-id-1");
        derivedProvidedProd.setProductName("derived-provided-product-name-1");
        Set<ProvidedProduct> derivedProvidedProducts = new HashSet<>();
        derivedProvidedProducts.add(derivedProvidedProd);
        source.setDerivedProvidedProductDtos(derivedProvidedProducts);

        source.setEndDate(new Date());
        source.setStartDate(new Date());

        return source;
    }

    @Override
    protected PoolDTO initDestinationObject() {
        return new PoolDTO();
    }

    @Override
    protected void verifyOutput(Pool source, PoolDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getContractNumber(), dest.getContractNumber());
            assertEquals(source.getAccountNumber(), dest.getAccountNumber());
            assertEquals(source.getOrderNumber(), dest.getOrderNumber());
            assertEquals(source.getProductId(), dest.getProductId());
            assertEquals(source.getDerivedProductId(), dest.getDerivedProductId());
            assertEquals(source.getStartDate(), dest.getStartDate());
            assertEquals(source.getEndDate(), dest.getEndDate());

            if (childrenGenerated) {

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
                assertNull(dest.getBranding());
                assertNull(dest.getProvidedProducts());
                assertNull(dest.getDerivedProvidedProducts());
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
