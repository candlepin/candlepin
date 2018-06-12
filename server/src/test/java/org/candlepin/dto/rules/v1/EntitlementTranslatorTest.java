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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Entitlement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;



/**
 * Test suite for the EntitlementTranslator class.
 */
public class EntitlementTranslatorTest extends
    AbstractTranslatorTest<Entitlement, EntitlementDTO, EntitlementTranslator> {

    private PoolTranslatorTest poolTranslatorTest = new PoolTranslatorTest();

    @Override
    protected EntitlementTranslator initObjectTranslator() {
        this.poolTranslatorTest.initObjectTranslator();

        this.translator = new EntitlementTranslator();
        return this.translator;
    }

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.poolTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, Entitlement.class, EntitlementDTO.class);
    }

    @Override
    protected Entitlement initSourceObject() {
        Entitlement source = new Entitlement();
        source.setId("ent-id");
        source.setQuantity(1);

        source.setPool(this.poolTranslatorTest.initSourceObject());

        return source;
    }

    @Override
    protected EntitlementDTO initDestinationObject() {
        return new EntitlementDTO();
    }

    @Override
    protected void verifyOutput(Entitlement source, EntitlementDTO dest, boolean childrenGenerated) {
        if (source != null) {

            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getQuantity(), dest.getQuantity());

            if (childrenGenerated) {
                this.poolTranslatorTest.verifyOutput(source.getPool(), dest.getPool(), true);

                // These getters' returned fields live on the pool, so they're children.
                assertEquals(source.getStartDate(), dest.getStartDate());
                assertEquals(source.getEndDate(), dest.getEndDate());
            }
            else {
                assertNull(dest.getPool());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
