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

/**
 * Class meant to provide fully randomized instances of owner.
 *
 * Individual tests can then modify the instance according to their needs.
 */
public final class Owners {

    private static final String ACCESS_MODE_LIST = "org_environment,entitlement";
    private static final String ENT_ACCESS_MODE = "entitlement";
    private static final String SCA_ACCESS_MODE = "org_environment";

    private Owners() {
        throw new UnsupportedOperationException();
    }

    public static OwnerDTO random() {
        // todo fill in rest of the data
        return new OwnerDTO()
            .key(StringUtil.random("test_owner"))
            .displayName(StringUtil.random("Test Owner"))
            .contentAccessMode(ENT_ACCESS_MODE)
            .contentAccessModeList(ACCESS_MODE_LIST);
    }

    public static OwnerDTO randomSca() {
        return random()
            .contentAccessMode(SCA_ACCESS_MODE);
    }

    public static NestedOwnerDTO toNested(OwnerDTO owner) {
        return new NestedOwnerDTO()
            .key(requireNonNull(owner.getKey()))
            .id(owner.getId())
            .displayName(owner.getDisplayName())
            .contentAccessMode(owner.getContentAccessMode());
    }

}
