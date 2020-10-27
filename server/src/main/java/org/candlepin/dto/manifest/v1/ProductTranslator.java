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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;



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
    public ProductDTO populate(ModelTranslator translator, Product source, ProductDTO destination) {
        destination = super.populate(translator, source, destination);

        destination.setUuid(source.getUuid());
        destination.setMultiplier(source.getMultiplier());
        destination.setId(source.getId());
        destination.setName(source.getName());
        destination.setAttributes(source.getAttributes());
        destination.setDependentProductIds(source.getDependentProductIds());

        // Translate children products (recursive op)
        Product srcDerived = source.getDerivedProduct();
        destination.setDerivedProduct(srcDerived != null ? this.translate(translator, srcDerived) : null);

        Collection<Product> srcProvided = source.getProvidedProducts();
        Set<ProductDTO> destProvided = Collections.emptySet();

        if (srcProvided != null) {
            destProvided = new HashSet<>();

            for (Product provided : srcProvided) {
                if (provided != null) {
                    destProvided.add(this.translate(translator, provided));
                }
            }
        }

        destination.setProvidedProducts(destProvided);

        // Translate other children
        if (translator != null) {
            Collection<ProductContent> productContent = source.getProductContent();
            destination.setProductContent(Collections.emptyList());

            if (productContent != null) {
                ObjectTranslator<Content, ContentDTO> contentTranslator = translator
                    .findTranslatorByClass(Content.class, ContentDTO.class);

                for (ProductContent pc : productContent) {
                    if (pc != null) {
                        ContentDTO dto = contentTranslator.translate(translator, pc.getContent());

                        if (dto != null) {
                            destination.addContent(dto, pc.isEnabled());
                        }
                    }
                }
            }
        }
        else {
            destination.setProductContent(Collections.emptyList());
        }

        return destination;
    }

}
