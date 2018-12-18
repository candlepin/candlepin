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
import org.candlepin.model.PoolQuantity;


/**
 * Test suite for the CertificateTranslator class
 */
public class PoolQuantityTranslatorTest extends
    AbstractTranslatorTest<PoolQuantity, PoolQuantityDTO, PoolQuantityTranslator> {

    protected PoolQuantityTranslator translator = new PoolQuantityTranslator();

    protected PoolTranslatorTest poolTranslatorTest = new PoolTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.poolTranslatorTest.initModelTranslator(modelTranslator);
        modelTranslator.registerTranslator(this.translator, PoolQuantity.class, PoolQuantityDTO.class);
    }

    @Override
    protected PoolQuantityTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected PoolQuantity initSourceObject() {
        PoolQuantity poolQuantity = new PoolQuantity(this.poolTranslatorTest.initSourceObject(), 8);
        return poolQuantity;
    }

    @Override
    protected PoolQuantityDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new PoolQuantityDTO();
    }

    @Override
    protected void verifyOutput(PoolQuantity source, PoolQuantityDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getQuantity(), dest.getQuantity());

            if (childrenGenerated) {
                this.poolTranslatorTest.verifyOutput(source.getPool(), dest.getPool(), true);
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
