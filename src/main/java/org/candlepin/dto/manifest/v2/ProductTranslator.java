/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

/**
 * Translation of Product objects to version 2 manifest ProductDTOs
 * <p>
 * Applies the configured namespace filter to roots, derived products, provided products, and content.
 * Excluded products translate to null; excluded references are omitted from the DTO.
 */
public class ProductTranslator extends TimestampedEntityTranslator<Product, ProductDTO> {

    /**
     * Initializes the product translator
     *
     */
    @Inject
    public ProductTranslator() { }

    @Override
    public ProductDTO translate(Product source) {
        return this.translate(null, source);
    }

    @Override
    public ProductDTO translate(ModelTranslator translator, Product source) {
        return source != null ? this.populate(translator, source, new ProductDTO()) : null;
    }

    @Override
    public ProductDTO populate(Product source, ProductDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns null without modifying the destination when the source namespace is excluded.
     */
    @Override
    public ProductDTO populate(ModelTranslator translator, Product source, ProductDTO destination) {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("source and destination must not be null");
        }
        destination = super.populate(translator, source, destination);

        destination.setId(source.getId());
        destination.setName(source.getName());
        destination.setAttributes(source.getAttributes());

        Product derivedProduct = source.getDerivedProduct();
        destination.setDerivedProduct(derivedProduct != null ? derivedProduct.getId() : null);

        Set<String> providedProducts = new HashSet<>();
        if (source.getProvidedProducts() != null) {
            for (Product providedProduct : source.getProvidedProducts()) {
                providedProducts.add(providedProduct.getId());
            }
        }
        destination.setProvidedProducts(providedProducts);

        Map<String, ContentDTO> content = new HashMap<>();
        Set<BrandingDTO> brandings = new HashSet<>();
        if (translator != null) {
            if (source.getProductContent() != null) {
                for (ProductContent productContent : source.getProductContent()) {
                    if (productContent != null && productContent.getContent() != null) {
                        Content sourceContent = productContent.getContent();
                        ContentDTO translatedContent = translator.translate(sourceContent, ContentDTO.class);
                        translatedContent.setEnabled(productContent.isEnabled());
                        content.put(sourceContent.getId(), translatedContent);
                    }
                }
            }
            if (source.getBranding() != null) {
                source.getBranding()
                    .forEach(branding -> {
                        if (branding != null) {
                            brandings.add(translator.translate(branding, BrandingDTO.class));
                        }
                    });
            }
        }
        destination.setProductContent(content);
        destination.setBranding(brandings);

        return destination;
    }
}
