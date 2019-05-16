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
import org.candlepin.model.Owner;



/**
 * Test suite for the OwnerTranslator class
 */
public class OwnerTranslatorTest extends AbstractTranslatorTest<Owner, OwnerDTO, OwnerTranslator> {

    protected OwnerTranslator translator = new OwnerTranslator();

    @Override
    public void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Owner.class, OwnerDTO.class);
    }

    @Override
    public OwnerTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    public Owner initSourceObject() {
        Owner source = new Owner();
        source.setId("id");
        source.setDefaultServiceLevel("service_level");

        return source;
    }

    @Override
    protected OwnerDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new OwnerDTO();
    }

    @Override
    public void verifyOutput(Owner source, OwnerDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getDefaultServiceLevel(), dest.getDefaultServiceLevel());
        }
        else {
            assertNull(dest);
        }
    }
}
