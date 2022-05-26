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

import static java.util.Objects.requireNonNull;

import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

public final class Owners {

    private Owners() {
        throw new UnsupportedOperationException();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static OwnerDTO random() {
        return new Builder().build();
    }

    public static OwnerDTO randomSca() {
        return new Builder()
            .scaEnabled()
            .build();
    }

    public static NestedOwnerDTO toNested(OwnerDTO owner) {
        return new NestedOwnerDTO()
            .key(requireNonNull(owner.getKey()))
            .id(owner.getId())
            .displayName(owner.getDisplayName())
            .contentAccessMode(owner.getContentAccessMode());
    }

    public static class Builder {

        private NestedOwnerDTO parent = null;
        private String contentAccessMode = "entitlement";
        private String contentAccessModeList = "entitlement";

        // withers...
        public Builder withContentAccess(String contentAccessMode) {
            this.contentAccessMode = contentAccessMode;
            return this;
        }

        public Builder withContentAccessList(String contentAccessModeList) {
            this.contentAccessModeList = contentAccessModeList;
            return this;
        }

        public Builder scaEnabled() {
            this.withContentAccess("org_environment")
                .withContentAccessList("org_environment,entitlement");
            return this;
        }

        public Builder withParent(NestedOwnerDTO parentOwner) {
            this.parent = parentOwner;
            return this;
        }

        public OwnerDTO build() {
            OwnerDTO owner = new OwnerDTO();
            owner.setKey(StringUtil.random("test_owner"));
            owner.displayName(StringUtil.random("Test Owner"));
            owner.setContentAccessMode(this.contentAccessMode);
            owner.setContentAccessModeList(this.contentAccessModeList);
            owner.setParentOwner(this.parent);
            // todo fill in rest of the data
            return owner;
        }
    }

}
