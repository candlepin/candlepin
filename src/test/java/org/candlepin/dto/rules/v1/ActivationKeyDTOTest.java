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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.AbstractDTOTest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the ActivationKeyDTO class for Rules
 */
public class ActivationKeyDTOTest extends AbstractDTOTest<ActivationKeyDTO> {

    protected Map<String, Object> values;

    public ActivationKeyDTOTest() {
        super(ActivationKeyDTO.class);

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");

        Set<ActivationKeyDTO.ActivationKeyPoolDTO> pools = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            ActivationKeyDTO.ActivationKeyPoolDTO akPool = new ActivationKeyDTO.ActivationKeyPoolDTO();

            ActivationKeyDTO.InternalPoolDTO internalPool = new ActivationKeyDTO.InternalPoolDTO();
            internalPool.setId("internal-id-" + i);
            Map<String, String> attrs = new HashMap<>();
            attrs.put("attr1", "val1");
            attrs.put("attr2", "val2");
            internalPool.setAttributes(attrs);
            Map<String, String> productAttrs = new HashMap<>();
            productAttrs.put("prod-attr1", "prod-val1");
            productAttrs.put("prod-attr2", "prod-val1");
            internalPool.setProductAttributes(productAttrs);

            akPool.setPool(internalPool);
            akPool.setQuantity((long) i);

            pools.add(akPool);
        }

        this.values.put("Pools", pools);
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
