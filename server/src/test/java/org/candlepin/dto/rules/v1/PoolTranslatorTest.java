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
package org.candlepin.dto.rules.v1;


import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;

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
 * Test suite for the PoolTranslator (Rules) class.
 */
public class PoolTranslatorTest extends AbstractTranslatorTest<Pool, PoolDTO, PoolTranslator> {

    protected PoolTranslator translator = new PoolTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
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

        source.setHasSharedAncestor(true);

        source.setQuantity(1L);
        source.setStartDate(new Date());
        source.setEndDate(new Date());

        Map<String, String> attributes = new HashMap<>();
        attributes.put(Pool.Attributes.SOURCE_POOL_ID, "true");
        source.setAttributes(attributes);

        source.setRestrictedToUsername("restricted-to-username-value");
        source.setConsumed(6L);

        source.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");

        Product derivedProduct = new Product();
        derivedProduct.setId("derived-product-id-2");
        derivedProduct.setName("derived-product-name-2");
        derivedProduct.setAttributes(new HashMap<>());
        derivedProduct.setAttribute(Product.Attributes.ARCHITECTURE, "POWER");
        derivedProduct.setAttribute(Product.Attributes.STACKING_ID, "2221");
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
            assertEquals(source.hasSharedAncestor(), dest.hasSharedAncestor());
            assertEquals(source.getQuantity(), dest.getQuantity());
            assertEquals(source.getStartDate(), dest.getStartDate());
            assertEquals(source.getEndDate(), dest.getEndDate());
            assertEquals(source.getAttributes(), dest.getAttributes());
            assertEquals(source.getRestrictedToUsername(), dest.getRestrictedToUsername());
            assertEquals(source.getConsumed(), dest.getConsumed());
            assertEquals(source.getProductId(), dest.getProductId());
            assertEquals(source.getProductAttributes(), dest.getProductAttributes());
            assertEquals(source.getDerivedProductId(), dest.getDerivedProductId());

            if (childrenGenerated) {

                Set<Product> sourceProducts = source.getProvidedProducts();
                Set<PoolDTO.ProvidedProductDTO> productsDTO = dest.getProvidedProducts();
                verifyProductsOutput(sourceProducts, productsDTO);

                Set<Product> sourceDerivedProducts = source.getDerivedProvidedProducts();
                Set<PoolDTO.ProvidedProductDTO> derivedProductsDTO = dest.getDerivedProvidedProducts();
                verifyProductsOutput(sourceDerivedProducts, derivedProductsDTO);
            }
            else {
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
