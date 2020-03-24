/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.GuestId;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * Test suite for the GuestIdArrayElementTranslator class
 */
public class GuestIdArrayElementTranslatorTest extends AbstractTranslatorTest<GuestId,
    GuestIdDTOArrayElement, GuestIdArrayElementTranslator> {

    protected GuestIdArrayElementTranslator guestIdTranslator = new GuestIdArrayElementTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.guestIdTranslator, GuestId.class,
            GuestIdDTOArrayElement.class);
    }

    @Override
    protected GuestIdArrayElementTranslator initObjectTranslator() {
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
        source.setCreated(new Date());
        source.setUpdated(new Date());

        return source;
    }

    @Override
    protected GuestIdDTOArrayElement initDestinationObject() {
        // Nothing fancy to do here.
        return new GuestIdDTOArrayElement();
    }

    @Override
    protected void verifyOutput(GuestId source, GuestIdDTOArrayElement dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getGuestId(), dto.getGuestId());
            assertEquals(source.getCreated(), dto.getCreated() != null ?
                new Date(dto.getCreated().toInstant().toEpochMilli()) : null);
            assertEquals(source.getUpdated(), dto.getUpdated() != null ?
                new Date(dto.getUpdated().toInstant().toEpochMilli()) : null);

            // No need to assert for translation of attributes field, as that is not present on this
            // version of the DTO.
        }
        else {
            assertNull(dto);
        }
    }
}
