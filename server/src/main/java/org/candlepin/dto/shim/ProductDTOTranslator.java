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
package org.candlepin.dto.shim;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;

import java.util.Collection;
import java.util.Collections;


/**
 * The ProductDTOTranslator provides translation from the new ProductDTOs (manifest import/export)
 * to the traditional ProductData DTO objects
 *
 */
public class ProductDTOTranslator implements ObjectTranslator<ProductDTO, ProductData> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductData translate(ProductDTO source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductData translate(ModelTranslator translator, ProductDTO source) {
        return source != null ? this.populate(translator, source, new ProductData()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductData populate(ProductDTO source, ProductData destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Note: the 'locked' field is always set to false and the 'href' field is not translated,
     * since these fields are not currently being exported.</p>
     */
    @Override
    public ProductData populate(ModelTranslator modelTranslator, ProductDTO source, ProductData dest) {
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

        // We manually set this to false since it is not included in the exported data.
        dest.setLocked(false);

        Collection<ProductDTO.ProductContentDTO> productContentDTOs = source.getProductContent();
        dest.setProductContent(Collections.emptyList());

        if (modelTranslator != null && productContentDTOs != null) {
            ObjectTranslator<ContentDTO, ContentData> contentDTOTranslator = modelTranslator
                .findTranslatorByClass(ContentDTO.class, ContentData.class);

            for (ProductDTO.ProductContentDTO pcdto : productContentDTOs) {
                if (pcdto != null && pcdto.getContent() != null) {
                    ContentData contentData = contentDTOTranslator.translate(modelTranslator,
                        pcdto.getContent());
                    dest.addContent(contentData, pcdto.isEnabled());
                }
            }
        }

        return dest;
    }
}
