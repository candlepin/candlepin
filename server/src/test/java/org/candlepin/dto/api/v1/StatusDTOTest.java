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
 * Test suite for the StatusDTO class
 */
public class StatusDTOTest extends AbstractDTOTest<StatusDTO> {

    protected Map<String, Object> values;

    public StatusDTOTest() {
        super(StatusDTO.class);

        Set<String> capabilities = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            capabilities.add("capability-" + i);
        }

        this.values = new HashMap<>();
        this.values.put("Mode", "test-mode");
        this.values.put("ModeReason", "mode-reason");
        this.values.put("ModeChangeTime", new Date());
        this.values.put("Result", true);
        this.values.put("Version", "test-version");
        this.values.put("RulesVersion", "rules-version");
        this.values.put("Release", "release-version");
        this.values.put("Standalone", true);
        this.values.put("TimeUTC", new Date());
        this.values.put("RulesSource", "rules-source");
        this.values.put("Capabilities", capabilities);
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
        // Since we're returning primitives for two methods, we need to convert them as necessary
        if (field != null && (field.equals("Result") || field.equals("Standalone")) && input == null) {
            return false;
        }

        return input;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullToCapabilities() throws Exception {
        StatusDTO dto = new StatusDTO();
        dto.addManagerCapability(null);
    }

    @Test
    public void testAddToEmptyCapabilitiesCollection() throws Exception {
        StatusDTO dto = new StatusDTO();

        String capability = "test-capability";
        assertTrue(dto.addManagerCapability(capability));

        assertEquals(1, dto.getManagerCapabilities().size());
        assertEquals(capability, dto.getManagerCapabilities().iterator().next());
    }

    @Test
    public void testAddDuplicateToCapabilitiesCollection() throws Exception {
        StatusDTO dto = new StatusDTO();
        String capability = "test-capability";
        assertTrue(dto.addManagerCapability(capability));

        assertEquals(1, dto.getManagerCapabilities().size());
        assertEquals(capability, dto.getManagerCapabilities().iterator().next());

        assertFalse(dto.addManagerCapability(capability));
        assertEquals(1, dto.getManagerCapabilities().size());
        assertEquals(capability, dto.getManagerCapabilities().iterator().next());
    }

    @Test
    public void testRemoveFromCapabilitiesCollectionWhenPresent() throws Exception {
        StatusDTO dto = new StatusDTO();
        String capability = "test-capability";
        assertTrue(dto.addManagerCapability(capability));

        assertEquals(1, dto.getManagerCapabilities().size());
        assertEquals(capability, dto.getManagerCapabilities().iterator().next());

        assertTrue(dto.removeManagerCapability(capability));
        assertEquals(0, dto.getManagerCapabilities().size());
    }

    @Test
    public void testRemoveFromCapabilitiesCollectionWhenAbsent() throws Exception {
        StatusDTO dto = new StatusDTO();
        String capability = "test-capability";
        assertTrue(dto.addManagerCapability(capability));

        assertFalse(dto.removeManagerCapability("DNE"));

        assertEquals(1, dto.getManagerCapabilities().size());
        assertEquals(capability, dto.getManagerCapabilities().iterator().next());
    }

}
