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
import org.candlepin.dto.api.v1.EnvironmentDTO.EnvironmentContentDTO;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Test suite for the EnvironmentDTO class
 */
public class EnvironmentDTOTest extends AbstractDTOTest<EnvironmentDTO> {

    protected ContentDTOTest contentDTOTest = new ContentDTOTest();

    protected Map<String, Object> values;

    public EnvironmentDTOTest() {
        super(EnvironmentDTO.class);

        Collection<EnvironmentContentDTO> environmentContent = new HashSet<>();

        for (int i = 0; i < 5; ++i) {
            ContentDTO content = this.contentDTOTest.getPopulatedDTOInstance();
            content.setId(content.getId() + "-" + i);
            environmentContent.add(new EnvironmentContentDTO(content, i % 2 != 0));
        }
        NestedOwnerDTO owner = new NestedOwnerDTO().id("OwnerId").displayName("Name").key("12345");

        this.values = new HashMap<>();
        this.values.put("Id", "test_value");
        this.values.put("Name", "test_value");
        this.values.put("Description", "test_value");
        this.values.put("EnvironmentContent", environmentContent);
        this.values.put("Owner", owner);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
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
        // Nothing to do here
        return input;
    }
}
