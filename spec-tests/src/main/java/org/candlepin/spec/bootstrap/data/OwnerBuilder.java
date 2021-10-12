/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */

package org.candlepin.spec.bootstrap.data;

import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;

public class OwnerBuilder {

    private NestedOwnerDTO parent = null;
    private String contentAccessMode = "entitlement";
    private String contentAccessModeList = "entitlement";

    // withers...
    public OwnerBuilder withContentAccess(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;
        return this;
    }

    public OwnerBuilder withContentAccessList(String contentAccessModeList) {
        this.contentAccessModeList = contentAccessModeList;
        return this;
    }

    public OwnerBuilder withParent(NestedOwnerDTO parentOwner) {
        this.parent = parentOwner;
        return this;
    }

    public OwnerDTO build() {
        OwnerDTO owner = new OwnerDTO();
        owner.setKey(Util.randomString("test_owner"));
        owner.displayName(Util.randomString("Test Owner"));
        owner.setContentAccessMode(this.contentAccessMode);
        owner.setContentAccessModeList(this.contentAccessModeList);
        owner.setParentOwner(this.parent);
        // todo fill in rest of the data
        return owner;
    }
}
