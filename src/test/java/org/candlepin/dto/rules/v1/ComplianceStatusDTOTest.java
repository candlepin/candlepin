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
import org.candlepin.dto.api.v1.DateRange;
import org.candlepin.util.Util;


import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * Test suite for the ComplianceStatusDTO (Rules) class
 */
public class ComplianceStatusDTOTest extends AbstractDTOTest<ComplianceStatusDTO> {

    protected Map<String, Object> values;

    public ComplianceStatusDTOTest() {
        super(ComplianceStatusDTO.class);

        Set<ComplianceReasonDTO> reasons = new HashSet<>();

        for (int i = 0; i < 3; ++i) {
            ComplianceReasonDTO reason = new ComplianceReasonDTO();
            reason.setKey("test-key-" + i);
            reason.setMessage("test-msg-" + i);

            Map<String, String> attributes = new HashMap<>();
            attributes.put("a1-" + i, "v1-" + i);
            attributes.put("a2-" + i, "v2-" + i);
            attributes.put("a3-" + i, "v3-" + i);

            reasons.add(reason);
        }

        Map<String, DateRange> ranges = new HashMap<>();

        for (int i = 0; i < 3; ++i) {
            DateRange range = new DateRange();

            Calendar sdc = Calendar.getInstance();
            sdc.add(Calendar.HOUR, i);

            range.setStartDate(Util.toDateTime(sdc.getTime()));

            Calendar edc = Calendar.getInstance();
            edc.add(Calendar.HOUR, i);
            edc.add(Calendar.YEAR, 1);

            range.setEndDate(Util.toDateTime(edc.getTime()));

            ranges.put("test_prod-" + i, range);
        }

        Map<String, Set<EntitlementDTO>> compliantProducts = new HashMap<>();
        Map<String, Set<EntitlementDTO>> partiallyCompliantProducts = new HashMap<>();
        Map<String, Set<EntitlementDTO>> partialStacks = new HashMap<>();

        for (int i = 0; i < 3; ++i) {
            compliantProducts.put("p" + i, this.generateEntitlementDTOs(3));
            partiallyCompliantProducts.put("p" + (3 + i), this.generateEntitlementDTOs(3));
            partialStacks.put("s" + i, this.generateEntitlementDTOs(3));
        }

        this.values = new HashMap<>();
        this.values.put("Status", "test-status");
        this.values.put("Compliant", Boolean.TRUE);
        this.values.put("Date", new Date());
        this.values.put("CompliantUntil", new Date());

        this.values.put("CompliantProducts", compliantProducts);
        this.values.put("NonCompliantProducts", Arrays.asList("p1", "p2", "p3"));
        this.values.put("PartiallyCompliantProducts", partiallyCompliantProducts);
        this.values.put("PartialStacks", partialStacks);
        this.values.put("ProductComplianceDateRanges", ranges);
        this.values.put("ComplianceReasons", reasons);
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
