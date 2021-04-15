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
package org.candlepin.dto.shim;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.api.v1.AttributeDTO;
import org.candlepin.dto.api.v1.BrandingDTO;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ProductContentDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.model.Branding;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.util.Util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The ProductDataTranslator provides translation from ProductData DTO objects to the new
 * ProductDTOs (API)
 */
public class ProductDataTranslator implements ObjectTranslator<ProductData, ProductDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO translate(ProductData source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO translate(ModelTranslator translator, ProductData source) {
        return source != null ? this.populate(translator, source, new ProductDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO populate(ProductData source, ProductDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO populate(
        ModelTranslator modelTranslator, ProductData source, ProductDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(source.getId())
            .uuid(source.getUuid())
            .name(source.getName())
            .multiplier(source.getMultiplier())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .attributes(toAttributes(source.getAttributes()))
            .productContent(new HashSet<>())
            .branding(new HashSet<>())
            .dependentProductIds(toSet(source))
            .href(source.getHref());

        if (modelTranslator != null) {
            Collection<ProductContentData> productContentData = source.getProductContent();
            if (productContentData != null && !productContentData.isEmpty()) {
                ObjectTranslator<ContentData, ContentDTO> contentTranslator = modelTranslator
                    .findTranslatorByClass(ContentData.class, ContentDTO.class);
                Set<ProductContentDTO> dtos = new HashSet<>();
                for (ProductContentData productContent : productContentData) {
                    if (productContent != null && productContent.getContent() != null) {
                        ContentDTO dto = contentTranslator
                            .translate(modelTranslator, productContent.getContent());
                        dtos.add(createContent(dto, productContent.isEnabled()));
                    }
                }
                dest.productContent(dtos);
            }
            else {
                dest.productContent(Collections.emptySet());
            }

            Collection<Branding> productBrandings = source.getBranding();
            if (productBrandings != null && !productBrandings.isEmpty()) {
                Set<BrandingDTO> dtos = new HashSet<>();
                for (Branding brand : productBrandings) {
                    if (brand != null) {
                        dtos.add(modelTranslator.translate(brand, BrandingDTO.class));
                    }
                }
                dest.setBranding(dtos);
            }
            else {
                dest.setBranding(Collections.emptySet());
            }
        }

        return dest;
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

    private HashSet<String> toSet(ProductData source) {
        if (source == null || source.getDependentProductIds() == null) {
            return null;
        }
        return new HashSet<>(source.getDependentProductIds());
    }

    private ProductContentDTO createContent(ContentDTO dto, boolean enabled) {
        if (dto == null || dto.getId() == null) {
            throw new IllegalArgumentException("dto references incomplete content");
        }

        return new ProductContentDTO()
            .content(dto)
            .enabled(enabled);
    }

}
