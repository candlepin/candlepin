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

public record ContentDTO(
    String id,
    String type,
    String label,
    String name,
    String vendor,
    String contentUrl,
    String requiredTags,
    String releaseVer,
    String gpgUrl,
    String arches
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String type;
        private String label;
        private String name;
        private String vendor;
        private String contentUrl;
        private String requiredTags;
        private String releaseVer;
        private String gpgUrl;
        private String arches;

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setType(String type) {
            this.type = type;
            return this;
        }

        public Builder setLabel(String label) {
            this.label = label;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setVendor(String vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder setContentUrl(String contentUrl) {
            this.contentUrl = contentUrl;
            return this;
        }

        public Builder setRequiredTags(String requiredTags) {
            this.requiredTags = requiredTags;
            return this;
        }

        public Builder setReleaseVer(String releaseVer) {
            this.releaseVer = releaseVer;
            return this;
        }

        public Builder setGpgUrl(String gpgUrl) {
            this.gpgUrl = gpgUrl;
            return this;
        }

        public Builder setArches(String arches) {
            this.arches = arches;
            return this;
        }

        public ContentDTO build() {
            return new ContentDTO(
                this.id,
                this.type,
                this.label,
                this.name,
                this.vendor,
                this.contentUrl,
                this.requiredTags,
                this.releaseVer,
                this.gpgUrl,
                this.arches
            );
        }
    }
}
