/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;




/**
 * Test suite for the EntitlementDTO class
 */
public class EntitlementDTOTest  extends AbstractDTOTest<EntitlementDTO> {

    protected Map<String, Object> values;

    public EntitlementDTOTest() {
        super(EntitlementDTO.class);

        this.values = new HashMap<String, Object>();

        PoolDTO pool = new PoolDTO();
        pool.setId("pool_id");
        pool.setProductId("pool_product_id");

        this.values.put("Id", "test-id");
        this.values.put("Pool", pool);
        this.values.put("Quantity", 1);
        this.values.put("StartDate", new Date());
        this.values.put("EndDate", new Date());
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
