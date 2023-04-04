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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.api.server.v1.AttributeDTO;
import org.candlepin.dto.api.server.v1.BrandingDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The ProductDataTranslator provides translation from ProductData DTO objects to the new
 * ProductDTOs (API)
 */
public class ProductInfoTranslator implements ObjectTranslator<ProductInfo, ProductDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO translate(ProductInfo source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO translate(ModelTranslator translator, ProductInfo source) {
        return source != null ? this.populate(translator, source, new ProductDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO populate(ProductInfo source, ProductDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO populate(
        ModelTranslator modelTranslator, ProductInfo source, ProductDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(source.getId())
            .name(source.getName())
            .multiplier(source.getMultiplier())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .attributes(toAttributes(source.getAttributes()))
            .dependentProductIds(toSet(source))
            .uuid(null)
            .href(null);

        if (modelTranslator != null) {
            dest.productContent(translateProductContent(modelTranslator, source))
                .branding(translateBranding(modelTranslator, source))
                .providedProducts(translateProvidedProducts(modelTranslator, source))
                .derivedProduct(modelTranslator.translate(source.getDerivedProduct(), ProductDTO.class));
        }
        else {
            dest.productContent(new HashSet<>())
                .branding(new HashSet<>())
                .providedProducts(new HashSet<>())
                .derivedProduct(null);
        }

        return dest;
    }

    private Set<ProductContentDTO> translateProductContent(
        ModelTranslator modelTranslator, ProductInfo source) {
        Collection<? extends ProductContentInfo> productContent = source.getProductContent();
        if (productContent == null || productContent.isEmpty()) {
            return Collections.emptySet();
        }

        ObjectTranslator<ContentInfo, ContentDTO> translator = modelTranslator
            .findTranslatorByClass(ContentInfo.class, ContentDTO.class);

        return productContent.stream()
            .filter(Objects::nonNull)
            .filter(c -> Objects.nonNull(c.getContent()))
            .map(contentData -> createContent(modelTranslator, translator, contentData))
            .collect(Collectors.toSet());
    }

    private ProductContentDTO createContent(ModelTranslator modelTranslator,
        ObjectTranslator<ContentInfo, ContentDTO> translator, ProductContentInfo content) {
        if (content == null || content.getContent() == null || content.getContent().getId() == null) {
            throw new IllegalArgumentException("dto references incomplete content");
        }

        return new ProductContentDTO()
            .content(translator.translate(modelTranslator, content.getContent()))
            .enabled(content.isEnabled());
    }

    private Set<BrandingDTO> translateBranding(ModelTranslator modelTranslator, ProductInfo source) {
        Collection<? extends BrandingInfo> productBrandings = source.getBranding();
        if (productBrandings == null || productBrandings.isEmpty()) {
            return Collections.emptySet();
        }

        return productBrandings.stream()
            .filter(Objects::nonNull)
            .map(modelTranslator.getStreamMapper(BrandingInfo.class, BrandingDTO.class))
            .collect(Collectors.toSet());
    }

    private Set<ProductDTO> translateProvidedProducts(ModelTranslator modelTranslator, ProductInfo source) {
        Collection<? extends ProductInfo> providedProducts = source.getProvidedProducts();
        if (providedProducts == null || providedProducts.isEmpty()) {
            return new HashSet<>();
        }

        return providedProducts.stream()
            .filter(Objects::nonNull)
            .map(modelTranslator.getStreamMapper(ProductInfo.class, ProductDTO.class))
            .collect(Collectors.toSet());
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

    private HashSet<String> toSet(ProductInfo source) {
        if (source == null || source.getDependentProductIds() == null) {
            return new HashSet<>();
        }
        return new HashSet<>(source.getDependentProductIds());
    }

}
