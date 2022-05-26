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

import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

public final class ActivationKeys {

    private ActivationKeys() {
        throw new UnsupportedOperationException();
    }

    public static ActivationKeyDTO random(OwnerDTO owner) {
        return new Builder()
            .withOwner(owner)
            .build();
    }
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private NestedOwnerDTO owner;

        public Builder withOwner(NestedOwnerDTO owner) {
            this.owner = owner;
            return this;
        }

        public Builder withOwner(OwnerDTO owner) {
            this.owner = Owners.toNested(owner);
            return this;
        }

        public ActivationKeyDTO build() {
            ActivationKeyDTO testActivationKey = new ActivationKeyDTO()
                .owner(this.owner)
                .name(StringUtil.random("test_activation_key"));
            return testActivationKey;
        }
    }

}
