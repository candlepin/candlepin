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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.candlepin.dto.AbstractDTOTest;

import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the ConsumerDTO class
 */
public class ConsumerDTOTest extends AbstractDTOTest<ConsumerDTO> {


    protected Map<String, Object> values;
    protected OwnerDTOTest ownerDTOTest = new OwnerDTOTest();

    public ConsumerDTOTest() {
        super(ConsumerDTO.class);

        ConsumerTypeDTO type = new ConsumerTypeDTO();
        type.setLabel("type_label");
        type.setManifest(true);

        Map<String, String> facts = new HashMap<>();
        for (int i = 0; i < 5; ++i) {
            facts.put("fact-" + i, "value-" + i);
        }

        Set<String> installedProducts = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            installedProducts.add("cip-" + i);
        }

        Set<String> capabilityDTOS = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            capabilityDTOS.add("capability-" + i);
        }

        Set<String> addOns = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            addOns.add("Add-On-" + i);
        }

        this.values = new HashMap<>();
        this.values.put("Uuid", "test-uuid");
        this.values.put("Username", "test-user-name");
        this.values.put("ServiceLevel", "test-service-level");
        this.values.put("Usage", "test-usage");
        this.values.put("Role", "test-role");
        this.values.put("AddOns", addOns);
        this.values.put("Owner", this.ownerDTOTest.getPopulatedDTOInstance());
        this.values.put("Facts", facts);
        this.values.put("InstalledProducts", installedProducts);
        this.values.put("Capabilities", capabilityDTOS);
        this.values.put("Type", type);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());

        this.values.put("addInstalledProduct", "product-id-to-add");
        this.values.put("removeInstalledProduct", "product-id-to-remove");
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

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullToInstalledProducts() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        dto.addInstalledProduct(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveNullFromInstalledProducts() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        dto.removeInstalledProduct(null);
    }

    @Test
    public void testAddToEmptyInstalledProductCollection() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addInstalledProduct("product-id-to-add"));

        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals("product-id-to-add", dto.getInstalledProducts().iterator().next());
    }

    @Test
    public void testAddDuplicateToInstalledProductCollection() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addInstalledProduct("product-id-to-add"));

        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals("product-id-to-add", dto.getInstalledProducts().iterator().next());

        assertFalse(dto.addInstalledProduct("product-id-to-add"));
        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals("product-id-to-add", dto.getInstalledProducts().iterator().next());
    }

    @Test
    public void testRemoveFromInstalledProductCollectionWhenPresent() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addInstalledProduct("product-id-to-remove"));

        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals("product-id-to-remove", dto.getInstalledProducts().iterator().next());

        assertTrue(dto.removeInstalledProduct("product-id-to-remove"));
        assertEquals(0, dto.getInstalledProducts().size());
    }

    @Test
    public void testRemoveFromInstalledProductCollectionWhenAbsent() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addInstalledProduct("product-id-to-remove"));

        assertFalse(dto.removeInstalledProduct("DNE"));

        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals("product-id-to-remove", dto.getInstalledProducts().iterator().next());
    }
}
