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

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.GuestId;

import java.util.HashMap;
import java.util.Map;



/**
 * Test suite for the GuestIdTranslator class
 */
public class GuestIdTranslatorTest extends AbstractTranslatorTest<GuestId, GuestIdDTO, GuestIdTranslator> {

    protected GuestIdTranslator guestIdTranslator = new GuestIdTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.guestIdTranslator, GuestId.class, GuestIdDTO.class);
    }

    @Override
    protected GuestIdTranslator initObjectTranslator() {
        return this.guestIdTranslator;
    }

    @Override
    protected GuestId initSourceObject() {
        GuestId source = new GuestId();

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib_1", "attrib_value_1");
        attributes.put("attrib_2", "attrib_value_2");
        attributes.put("attrib_3", "attrib_value_3");

        source.setId("test_id");
        source.setGuestId("test_guest_id");
        source.setAttributes(attributes);

        return source;
    }

    @Override
    protected GuestIdDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new GuestIdDTO();
    }

    @Override
    protected void verifyOutput(GuestId source, GuestIdDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getGuestId(), dest.getGuestId());
            assertEquals(source.getAttributes(), dest.getAttributes());
        }
        else {
            assertNull(dest);
        }
    }
}
