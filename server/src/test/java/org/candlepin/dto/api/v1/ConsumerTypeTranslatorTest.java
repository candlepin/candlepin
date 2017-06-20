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

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.DTOFactory;
import org.candlepin.model.ConsumerType;

import static org.junit.Assert.*;

import junitparams.JUnitParamsRunner;

import org.junit.runner.RunWith;



/**
 * Test suite for the ConsumerTypeTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class ConsumerTypeTranslatorTest extends
    AbstractTranslatorTest<ConsumerType, ConsumerTypeDTO, ConsumerTypeTranslator> {

    protected ConsumerTypeTranslator translator = new ConsumerTypeTranslator();

    @Override
    protected void initFactory(DTOFactory factory) {
        factory.registerTranslator(ConsumerType.class, this.translator);
    }

    @Override
    protected ConsumerTypeTranslator initTranslator() {
        return this.translator;
    }

    @Override
    protected ConsumerType initSourceEntity() {
        ConsumerType type = new ConsumerType();

        type.setId("type_id");
        type.setLabel("type_label");
        type.setManifest(true);

        return type;
    }

    @Override
    protected ConsumerTypeDTO initDestDTO() {
        // Nothing fancy to do here.
        return new ConsumerTypeDTO();
    }

    @Override
    protected void verifyDTO(ConsumerType source, ConsumerTypeDTO dto, boolean childrenGenerated) {

        if (source != null) {
            ConsumerType src = (ConsumerType) source;
            ConsumerTypeDTO dest = (ConsumerTypeDTO) dto;

            // This DTO does not have any nested objects, so we don't need to worry about the
            // childrenGenerated flag

            assertEquals(src.getId(), dest.getId());
            assertEquals(src.getLabel(), dest.getLabel());
            assertEquals(src.isManifest(), dest.isManifest());
        }
        else {
            assertNull(dto);
        }
    }
}
