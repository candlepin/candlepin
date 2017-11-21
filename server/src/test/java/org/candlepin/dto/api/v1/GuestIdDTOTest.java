/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
import java.util.HashMap;
import java.util.Map;

/**
 * Test suite for the GuestIdDTO class
 */
public class GuestIdDTOTest extends AbstractDTOTest<GuestIdDTO> {

    protected Map<String, Object> values;

    public GuestIdDTOTest() {
        super(GuestIdDTO.class);
        Map<String, String> attributes = new HashMap<String, String>();

        for (int i = 0; i < 5; ++i) {
            attributes.put("attrib-" + i, "value-" + i);
        }

        this.values = new HashMap<String, Object>();
        this.values.put("Id", "test_value");
        this.values.put("GuestId", "test_value");
        this.values.put("Attributes", attributes);
    }

    /**
     * @{inheritDocs}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * @{inheritDocs}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }

}
