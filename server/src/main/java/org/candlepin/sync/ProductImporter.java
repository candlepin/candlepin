/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.sync;

import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.model.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;



/**
 * ProductImporter
 */
public class ProductImporter {
    private static Logger log = LoggerFactory.getLogger(ProductImporter.class);

    public ProductImporter() {
        // Intentionally left empty
    }

    public ProductDTO createObject(ObjectMapper mapper, Reader reader, Owner owner) throws IOException {

        ProductDTO importedProduct = mapper.readValue(reader, ProductDTO.class);

        // Make sure the (UU)ID's are null, otherwise Hibernate thinks these are
        // detached entities.
        importedProduct.setUuid(null);

        // Multiplication has already happened on the upstream candlepin. set this to 1
        // so we can use multipliers on local products if necessary.
        importedProduct.setMultiplier(1L);

        if (importedProduct.getProductContent() != null) {
            // Update attached content and ensure it isn't malformed
            for (ProductDTO.ProductContentDTO pc : importedProduct.getProductContent()) {
                ContentDTO content = pc.getContent();

                // Clear the UUID
                content.setUuid(null);

                // Fix the vendor string if it is/was cleared (BZ 990113)
                if (StringUtils.isBlank(content.getVendor())) {
                    content.setVendor("unknown");
                }

                // On standalone servers we will set metadata expire to 1 second so
                // clients an immediately get changes to content when published on the
                // server. We would use 0, but the client plugin interprets this as unset
                // and ignores it completely resulting in the default yum values being
                // used.
                //
                // We know this is a standalone server due to the fact that import is
                // being used, so there is no need to guard this behavior.
                content.setMetadataExpiration(1L);
            }
        }

        return importedProduct;
    }

}
