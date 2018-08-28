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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.AbstractDTOTest;

import java.util.HashMap;
import java.util.Map;



/**
 * Test suite for the ComplianceReasonDTO (Rules) class
 */
public class ComplianceReasonDTOTest extends AbstractDTOTest<ComplianceReasonDTO> {

    protected Map<String, Object> values;

    public ComplianceReasonDTOTest() {
        super(ComplianceReasonDTO.class);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("a1", "v1");
        attributes.put("a2", "v2");
        attributes.put("a3", "v3");

        this.values = new HashMap<>();
        this.values.put("Key", "test-key");
        this.values.put("Message", "test-message");
        this.values.put("Attributes", attributes);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        return input;
    }

}
