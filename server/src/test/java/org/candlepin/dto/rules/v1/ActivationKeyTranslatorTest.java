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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyPool;

import java.util.HashSet;
import java.util.Set;



/**
 * Test suite for the ActivationKeyTranslator class for Rules
 */
public class ActivationKeyTranslatorTest extends
    AbstractTranslatorTest<ActivationKey, ActivationKeyDTO, ActivationKeyTranslator> {

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, ActivationKey.class, ActivationKeyDTO.class);
    }

    @Override
    protected ActivationKeyTranslator initObjectTranslator() {
        this.translator = new ActivationKeyTranslator();
        return this.translator;
    }

    @Override
    protected ActivationKey initSourceObject() {
        ActivationKey source = new ActivationKey();
        source.setId("key-id");

        Set<ActivationKeyPool> akpools = new HashSet<>();
        for (int i = 0; i < 3; ++i) {
            Product mktProduct = new Product()
                .setId("mkt_product-" + i);

            Pool pool = new Pool()
                .setId("test_pool-" + i)
                .setProduct(mktProduct);

            for (int j = 0; j < 3; ++j) {
                pool.setAttribute("pool_attr-key" + j, "pool_attr-value" + j);
                mktProduct.setAttribute("prod_attr-key" + j, "prod_attr-value" + j);
            }

            ActivationKeyPool akp = new ActivationKeyPool(source, pool, 1L);

            akpools.add(akp);
        }

        source.setPools(akpools);

        return source;
    }

    @Override
    protected ActivationKeyDTO initDestinationObject() {
        return new ActivationKeyDTO();
    }

    @Override
    protected void verifyOutput(ActivationKey source, ActivationKeyDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());

            for (ActivationKeyPool akPool : source.getPools()) {
                boolean verified = false;
                for (ActivationKeyDTO.ActivationKeyPoolDTO akPoolDto : dest.getPools()) {
                    assertNotNull(akPool);
                    assertNotNull(akPoolDto);
                    assertNotNull(akPoolDto.getPool());

                    if (akPoolDto.getPool().getId().equals(akPool.getPool().getId())) {
                        assertEquals(akPool.getQuantity(), akPoolDto.getQuantity());
                        assertEquals(akPool.getPool().getAttributes(), akPoolDto.getPool().getAttributes());
                        assertEquals(akPool.getPool().getProductAttributes(),
                            akPoolDto.getPool().getProductAttributes());
                        verified = true;
                    }
                }
                assertTrue(verified);
            }
        }
        else {
            assertNull(dest);
        }
    }
}
