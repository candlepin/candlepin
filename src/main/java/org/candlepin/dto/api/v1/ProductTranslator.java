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
import org.candlepin.model.Branding;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.util.Util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;



/**
 * The ProductTranslator provides translation from Product model objects to ProductDTOs
 */
public class ProductTranslator implements ObjectTranslator<Product, ProductDTO> {

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
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .uuid(source.getUuid())
            .id(source.getId())
            .name(source.getName())
            .multiplier(source.getMultiplier())
            .href(source.getHref())
            .attributes(toAttributes(source.getAttributes()))
            .productContent(new HashSet<>())
            .branding(new HashSet<>())
            .dependentProductIds(new HashSet<>(source.getDependentProductIds()));

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
            if (productContent != null) {
                ObjectTranslator<ProductContent, ProductContentDTO> contentTranslator = translator
                    .findTranslatorByClass(ProductContent.class, ProductContentDTO.class);

                Set<ProductContentDTO> content = new HashSet<>();
                for (ProductContent pc : productContent) {
                    if (pc != null) {
                        ProductContentDTO dto = contentTranslator.translate(translator, pc);
                        if (dto != null) {
                            content.add(dto);
                        }
                    }
                }
                destination.productContent(content);
            }
            else {
                destination.productContent(Collections.emptySet());
            }

            Collection<Branding> branding = source.getBranding();
            if (branding != null && !branding.isEmpty()) {
                Set<BrandingDTO> dtos = new HashSet<>();
                for (Branding brand : branding) {
                    if (brand != null) {
                        dtos.add(translator.translate(brand, BrandingDTO.class));
                    }
                }
                destination.setBranding(dtos);
            }
            else {
                destination.setBranding(Collections.emptySet());
            }
        }
        else {
            destination.productContent(Collections.emptySet());
            destination.setBranding(Collections.emptySet());
        }

        return destination;
    }

    private List<AttributeDTO> toAttributes(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return source.entrySet().stream()
            .map(this::toAttribute)
            .collect(Collectors.toList());
    }

    private AttributeDTO toAttribute(Map.Entry<String, String> entry) {
        return new AttributeDTO()
            .name(entry.getKey())
            .value(entry.getValue());
    }

}
