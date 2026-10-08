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

import java.util.Map;
import java.util.Set;

public record ProductDTO(
    String id,
    String name,
    Map<String, String> attributes,
    ProductDTO derivedProduct,
    Map<String, ContentDTO> productContent,
    Set<BrandingDTO> brandings
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private Map<String, String> attributes;
        private ProductDTO derivedProduct;
        private Map<String, ContentDTO> productContent;
        private Set<BrandingDTO> brandings;

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder setDerivedProduct(ProductDTO derivedProduct) {
            this.derivedProduct = derivedProduct;
            return this;
        }

        public Builder setProductContent(Map<String, ContentDTO> productContent) {
            this.productContent = productContent;
            return this;
        }

        public Builder setBrandings(Set<BrandingDTO> brandings) {
            this.brandings = brandings;
            return this;
        }

        public ProductDTO build() {
            return new ProductDTO(
                this.id,
                this.name,
                this.attributes,
                this.derivedProduct,
                this.productContent,
                this.brandings
            );
        }
    }
}
