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

import java.util.Date;

public record MetaDTO(
    String manifestVersion,
    String version,
    Date created,
    String principalName,
    String webAppPrefix,
    String cdnLabel
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String manifestVersion;
        private String version;
        private Date created;
        private String principalName;
        private String webAppPrefix;
        private String cdnLabel;

        public Builder setManifestVersion(String manifestVersion) {
            this.manifestVersion = manifestVersion;
            return this;
        }

        public Builder setVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder setCreated(Date created) {
            this.created = created;
            return this;
        }

        public Builder setPrincipalName(String principalName) {
            this.principalName = principalName;
            return this;
        }

        public Builder setWebAppPrefix(String webAppPrefix) {
            this.webAppPrefix = webAppPrefix;
            return this;
        }

        public Builder setCdnLabel(String cdnLabel) {
            this.cdnLabel = cdnLabel;
            return this;
        }

        public MetaDTO build() {
            return new MetaDTO(
                this.manifestVersion,
                this.version,
                this.created,
                this.principalName,
                this.webAppPrefix,
                this.cdnLabel
            );
        }
    }
}
