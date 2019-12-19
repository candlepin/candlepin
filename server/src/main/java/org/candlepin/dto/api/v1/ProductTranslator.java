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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;

import java.util.Collection;
import java.util.Collections;



/**
 * The ProductTranslator provides translation from Product model objects to ProductDTOs
 */
public class ProductTranslator extends TimestampedEntityTranslator<Product, ProductDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO translate(Product source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO translate(ModelTranslator translator, Product source) {
        return source != null ? this.populate(translator, source, new ProductDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO populate(Product source, ProductDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO populate(ModelTranslator modelTranslator, Product source, ProductDTO destination) {
        destination = super.populate(modelTranslator, source, destination);

        destination.setUuid(source.getUuid());
        destination.setId(source.getId());
        destination.setName(source.getName());
        destination.setMultiplier(source.getMultiplier());
        destination.setHref(source.getHref());
        destination.setLocked(source.isLocked());
        destination.setAttributes(source.getAttributes());
        destination.setDependentProductIds(source.getDependentProductIds());

        if (modelTranslator != null) {
            Collection<ProductContent> productContent = source.getProductContent();
            destination.setProductContent(Collections.emptyList());

            if (productContent != null) {
                ObjectTranslator<Content, ContentDTO> contentTranslator = modelTranslator
                    .findTranslatorByClass(Content.class, ContentDTO.class);

                for (ProductContent pc : productContent) {
                    if (pc != null) {
                        ContentDTO dto = contentTranslator.translate(modelTranslator, pc.getContent());

                        if (dto != null) {
                            destination.addContent(dto, pc.isEnabled());
                        }
                    }
                }
            }

            Collection<Branding> branding = source.getBranding();
            if (branding != null && !branding.isEmpty()) {
                for (Branding brand : branding) {
                    if (brand != null) {
                        destination.addBranding(modelTranslator.translate(brand, BrandingDTO.class));
                    }
                }
            }
            else {
                destination.setBranding(Collections.emptySet());
            }

            Collection<Product> products = source.getProvidedProducts();

            if (products != null && !products.isEmpty()) {
                for (Product prod : products) {
                    if (prod != null) {
                        destination.addProvidedProduct(modelTranslator.translate(prod, ProductDTO.class));
                    }
                }
            }
            else {
                destination.setProvidedProducts(Collections.<ProductDTO>emptySet());
            }
        }
        else {
            destination.setProductContent(Collections.emptyList());
            destination.setBranding(Collections.emptySet());
        }

        return destination;
    }

}
