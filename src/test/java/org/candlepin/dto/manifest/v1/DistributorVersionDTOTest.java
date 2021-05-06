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
package org.candlepin.dto.manifest.v1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.dto.AbstractDTOTest;

import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the DistributorVersionDTO (manifest import/export) class
 */
public class DistributorVersionDTOTest extends AbstractDTOTest<DistributorVersionDTO> {

    protected Map<String, Object> values;

    public DistributorVersionDTOTest() {
        super(DistributorVersionDTO.class);

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Name", "test-name");
        this.values.put("DisplayName", "test-displayName");
        this.values.put("Updated", new Date());
        this.values.put("Created", new Date());

        Set<DistributorVersionDTO.DistributorVersionCapabilityDTO> dvCapabilityDTOs = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            DistributorVersionDTO.DistributorVersionCapabilityDTO dvCapabilityDTO =
                new DistributorVersionDTO.DistributorVersionCapabilityDTO("id-" + i, "name-" + i);
            dvCapabilityDTOs.add(dvCapabilityDTO);
        }

        this.values.put("Capabilities", dvCapabilityDTOs);
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

    @Test
    public void testAddCapabilityWithAbsentCapability() {
        DistributorVersionDTO dto = new DistributorVersionDTO();

        DistributorVersionDTO.DistributorVersionCapabilityDTO dvcDTO =
            new DistributorVersionDTO.DistributorVersionCapabilityDTO("dvc-id-1", "dvc-name-1");
        assertTrue(dto.addCapability(dvcDTO));
    }

    @Test
    public void testAddCapabilityWithPresentCapability() {
        DistributorVersionDTO dto = new DistributorVersionDTO();

        DistributorVersionDTO.DistributorVersionCapabilityDTO dvcDTO1 =
            new DistributorVersionDTO.DistributorVersionCapabilityDTO("dvc-id-2", "dvc-name-2");
        assertTrue(dto.addCapability(dvcDTO1));

        DistributorVersionDTO.DistributorVersionCapabilityDTO dvcDTO2 =
            new DistributorVersionDTO.DistributorVersionCapabilityDTO("dvc-id-2", "dvc-name-2");
        assertFalse(dto.addCapability(dvcDTO2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCapabilityWithNullInput() {
        DistributorVersionDTO dto = new DistributorVersionDTO();
        dto.addCapability(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCapabilityWithEmptyName() {
        DistributorVersionDTO dto = new DistributorVersionDTO();

        DistributorVersionDTO.DistributorVersionCapabilityDTO dvcDTO =
            new DistributorVersionDTO.DistributorVersionCapabilityDTO("dvc-id-3", "");
        dto.addCapability(dvcDTO);
    }
}
