/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.AbstractDTOTest;
import org.candlepin.model.SystemPurposeAttributeType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unit test for SystemPurposeAttributesDTO
 */
public class SystemPurposeAttributesDTOTest extends AbstractDTOTest<SystemPurposeAttributesDTO> {

    protected Map<String, Object> values;

    public SystemPurposeAttributesDTOTest() {
        super(SystemPurposeAttributesDTO.class);

        this.values = new HashMap<>();

        NestedOwnerDTO owner = new NestedOwnerDTO();
        owner.setId("owner-id");
        owner.setKey("owner-key");
        owner.setDisplayName("owner-name");

        Map<String, Set<String>> attributes = new HashMap<>();
        Set<String> attrSet = new HashSet<>();
        attrSet.add("EUS");
        attrSet.add("Awesome Mode");

        Set<String> attrSet2 = new HashSet<>();
        attrSet2.add("Server");

        attributes.put(SystemPurposeAttributeType.ADDONS.toString(), attrSet);
        attributes.put(SystemPurposeAttributeType.ROLES.toString(), attrSet2);

        this.values.put("Owner", owner);
        this.values.put("SystemPurposeAttributes", attributes);
    }

    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }
}
