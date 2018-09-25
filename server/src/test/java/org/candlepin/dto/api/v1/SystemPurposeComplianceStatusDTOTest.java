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

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the ComplianceStatusDTO class
 */
public class SystemPurposeComplianceStatusDTOTest extends AbstractDTOTest<SystemPurposeComplianceStatusDTO> {

    protected Map<String, Object> values;

    public SystemPurposeComplianceStatusDTOTest() {
        super(SystemPurposeComplianceStatusDTO.class);

        Set<String> reasons = new HashSet<>();

        for (int i = 0; i < 3; ++i) {
            reasons.add("test-msg-" + i);
        }

        Set<String> addOns = new HashSet<>();
        for (int i = 0; i < 3; ++i) {
            addOns.add("add-ons-" + i);
        }

        Map<String, Set<EntitlementDTO>> compliantRole = new HashMap<>();
        Map<String, Set<EntitlementDTO>> compliantUsage = new HashMap<>();
        Map<String, Set<EntitlementDTO>> compliantSLA = new HashMap<>();
        Map<String, Set<EntitlementDTO>> compliantAddOns = new HashMap<>();
        Map<String, Set<EntitlementDTO>> nonPreferredSLA = new HashMap<>();
        Map<String, Set<EntitlementDTO>> nonPreferredUsage = new HashMap<>();

        for (int i = 0; i < 3; ++i) {
            compliantRole.put("r" + i, this.generateEntitlementDTOs(i));
            compliantAddOns.put("a" + i, this.generateEntitlementDTOs(i));
            compliantUsage.put("u" + i, this.generateEntitlementDTOs(i));
            compliantSLA.put("s" + i, this.generateEntitlementDTOs(i));
            nonPreferredSLA.put("nps" + i, this.generateEntitlementDTOs(i));
            nonPreferredUsage.put("npu" + i, this.generateEntitlementDTOs(i));
        }

        this.values = new HashMap<>();
        this.values.put("Status", "test-status");
        this.values.put("Compliant", Boolean.TRUE);
        this.values.put("Date", new Date());
        this.values.put("NonCompliantRole", "nonCompliantRole");
        this.values.put("NonCompliantSLA", "nonCompliantSLA");
        this.values.put("NonCompliantUsage", "nonCompliantUsage");
        this.values.put("NonCompliantAddOns", addOns);

        this.values.put("CompliantRole", compliantRole);
        this.values.put("CompliantAddOns", compliantAddOns);
        this.values.put("CompliantUsage", compliantUsage);
        this.values.put("CompliantSLA", compliantSLA);
        this.values.put("NonPreferredSLA", nonPreferredSLA);
        this.values.put("NonPreferredUsage", nonPreferredUsage);
        this.values.put("Reasons", reasons);
    }

    private Set<EntitlementDTO> generateEntitlementDTOs(int count) {
        Set<EntitlementDTO> ents = new HashSet<>();

        for (int i = 0; i < count; ++i) {
            EntitlementDTO dto = new EntitlementDTO();
            dto.setId("test-dto-" + i);

            ents.add(dto);
        }

        return ents;
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
