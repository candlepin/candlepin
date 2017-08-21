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
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.ProductContentData;

import java.util.Collection;



/**
 * The ProductDataTranslator provides translation from ProductData DTO objects to the new
 * ProductDTOs
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
    public ProductDTO populate(ModelTranslator modelTranslator, ProductData source, ProductDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.setCreated(source.getCreated());
        dest.setUpdated(source.getUpdated());

        dest.setUuid(source.getUuid());
        dest.setId(source.getId());
        dest.setName(source.getName());
        dest.setMultiplier(source.getMultiplier());
        dest.setAttributes(source.getAttributes());
        dest.setDependentProductIds(source.getDependentProductIds());
        dest.setHref(source.getHref());
        dest.setLocked(source.isLocked());

        Collection<ProductContentData> productContentData = source.getProductContent();
        dest.setProductContent(null);

        if (modelTranslator != null && productContentData != null) {
            ObjectTranslator<ContentData, ContentDTO> contentTranslator = modelTranslator
                .findTranslatorByClass(ContentData.class, ContentDTO.class);

            for (ProductContentData pcd : productContentData) {
                if (pcd != null && pcd.getContent() != null) {
                    ContentDTO dto = contentTranslator.translate(modelTranslator, pcd.getContent());
                    dest.addContent(dto, pcd.isEnabled());
                }
            }
        }

        return dest;
    }
}
