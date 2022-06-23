/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.data.builder;

import static java.util.Objects.requireNonNullElseGet;

import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import java.time.OffsetDateTime;

public final class Consumers {

    private Consumers() {
        throw new UnsupportedOperationException();
    }

    public static ConsumerDTO random() {
        return new Builder().build();
    }
    public static ConsumerDTO random(OwnerDTO owner) {
        return new Builder()
            .withOwner(owner)
            .build();
    }

    public static class Builder {

        private String name = null;
        private NestedOwnerDTO owner = null;
        private ConsumerTypeDTO type = null;
        private OffsetDateTime lastCheckin = null;

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withOwner(NestedOwnerDTO owner) {
            this.owner = owner;
            return this;
        }

        public Builder withType(ConsumerTypeDTO type) {
            this.type = type;
            return this;
        }

        public Builder withOwner(OwnerDTO owner) {
            this.owner = Owners.toNested(owner);
            return this;
        }

        public Builder withLastCheckin(OffsetDateTime lastCheckin) {
            this.lastCheckin = lastCheckin;
            return this;
        }

        public ConsumerDTO build() {
            ConsumerDTO consumer = new ConsumerDTO();
            consumer.setName(requireNonNullElseGet(this.name, () -> StringUtil.random("test_consumer")));
            if (this.owner != null) {
                consumer.setOwner(this.owner);
            }
            if (this.type != null) {
                consumer.setType(this.type);
            }
            else {
                ConsumerTypeDTO type = new ConsumerTypeDTO();
                type.label("system");
                consumer.type(type);
            }

            if (lastCheckin != null) {
                consumer.setLastCheckin(this.lastCheckin);
            }

            consumer.putFactsItem("system.certificate_version", "3.3");
            // TODO: fill in rest of the data
            return consumer;
        }
    }

}
