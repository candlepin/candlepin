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

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.policy.js.compliance.ComplianceReason;

import java.util.HashMap;
import java.util.Map;



/**
 * Test suite for the ComplianceReasonTranslator class
 */
public class ComplianceReasonTranslatorTest extends
    AbstractTranslatorTest<ComplianceReason, ComplianceReasonDTO, ComplianceReasonTranslator> {

    @Override
    protected ComplianceReasonTranslator initObjectTranslator() {
        this.translator = new ComplianceReasonTranslator();
        return this.translator;
    }

    @Override
    protected void initModelTranslator(ModelTranslator translator) {
        translator.registerTranslator(this.translator, ComplianceReason.class, ComplianceReasonDTO.class);
    }

    @Override
    protected ComplianceReason initSourceObject() {
        ComplianceReason source = new ComplianceReason();

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib_1", "attrib_value_1");
        attributes.put("attrib_2", "attrib_value_2");
        attributes.put("attrib_3", "attrib_value_3");

        source.setKey("test_key");
        source.setMessage("test_message");
        source.setAttributes(attributes);

        return source;
    }

    @Override
    protected ComplianceReasonDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ComplianceReasonDTO();
    }

    @Override
    protected void verifyOutput(ComplianceReason source, ComplianceReasonDTO dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getKey(), dto.getKey());
            assertEquals(source.getMessage(), dto.getMessage());
            assertEquals(source.getAttributes(), dto.getAttributes());
        }
        else {
            assertNull(dto);
        }
    }
}
