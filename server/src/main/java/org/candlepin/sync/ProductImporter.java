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

import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;

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

    private ProductCurator curator;
    private ContentCurator contentCurator;

    public ProductImporter(ProductCurator curator, ContentCurator contentCurator) {
        this.curator = curator;
        this.contentCurator = contentCurator;
    }

    public Product createObject(ObjectMapper mapper, Reader reader, Owner owner) throws IOException {

        final Product importedProduct = mapper.readValue(reader, Product.class);
        // Make sure the (UU)ID's are null, otherwise Hibernate thinks these are
        // detached entities.
        importedProduct.setUuid(null);
        for (ProductAttribute a : importedProduct.getAttributes()) {
            a.setId(null);
        }

        // Multiplication has already happened on the upstream candlepin. set this to 1
        // so we can use multipliers on local products if necessary.
        importedProduct.setMultiplier(1L);

        // Update attached content and ensure it isn't malformed
        for (ProductContent pc : importedProduct.getProductContent()) {
            Content content = pc.getContent();

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
            content.setMetadataExpire(new Long(1));
        }

        return importedProduct;
    }

}
