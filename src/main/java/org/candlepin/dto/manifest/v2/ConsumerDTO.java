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

public record ConsumerDTO(
    String uuid,
    String name,
    ConsumerTypeDTO type,
    String ownerId,
    String urlWeb,
    String urlApi
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String uuid;
        private String name;
        private ConsumerTypeDTO type;
        private String ownerId;
        private String urlWeb;
        private String urlApi;

        public Builder setUuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setType(ConsumerTypeDTO type) {
            this.type = type;
            return this;
        }

        public Builder setOwnerId(String ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder setUrlWeb(String urlWeb) {
            this.urlWeb = urlWeb;
            return this;
        }

        public Builder setUrlApi(String urlApi) {
            this.urlApi = urlApi;
            return this;
        }

        public ConsumerDTO build() {
            return new ConsumerDTO(this.uuid, this.name, this.type, this.ownerId, this.urlWeb, this.urlApi);
        }
    }
}
