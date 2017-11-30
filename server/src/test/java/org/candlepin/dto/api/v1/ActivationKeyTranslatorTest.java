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

import junitparams.JUnitParamsRunner;
import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.junit.runner.RunWith;

import java.util.HashSet;

import static org.junit.Assert.*;

/**
 * Test suite for the ActivationKeyTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class ActivationKeyTranslatorTest extends
    AbstractTranslatorTest<ActivationKey, ActivationKeyDTO, ActivationKeyTranslator> {

    protected ActivationKeyTranslator translator = new ActivationKeyTranslator();

    protected OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);
        modelTranslator.registerTranslator(
            this.translator, ActivationKey.class, ActivationKeyDTO.class);
    }

    @Override
    protected ActivationKeyTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected ActivationKey initSourceObject() {
        ActivationKey source = new ActivationKey();
        source.setId("key-id");
        source.setName("key-name");
        source.setDescription("key-description");
        source.setOwner(this.ownerTranslatorTest.initSourceObject());
        source.setReleaseVer(new Release("key-release-ver"));
        source.setServiceLevel("key-service-level");
        source.setAutoAttach(true);

        Product prod = new Product();
        prod.setId("prod-1-id");
        source.setProducts(new HashSet<Product>());
        source.addProduct(prod);

        Pool pool = new Pool();
        pool.setId("pool-1-id");
        source.setPools(new HashSet<ActivationKeyPool>());
        source.addPool(pool, 1L);

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
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getDescription(), dest.getDescription());
            assertEquals(source.getServiceLevel(), dest.getServiceLevel());
            assertEquals(source.isAutoAttach(), dest.isAutoAttach());

            if (childrenGenerated) {
                this.ownerTranslatorTest
                        .verifyOutput(source.getOwner(), dest.getOwner(), true);

                for (Product prod : source.getProducts()) {
                    for (String prodDto : dest.getProducts()) {

                        assertNotNull(prodDto);

                        if (prodDto.equals(prod.getId())) {
                            // Nothing else to assert on, since prodDto only holds the product id.
                        }
                    }
                }

                for (ActivationKeyPool akPool : source.getPools()) {
                    for (ActivationKeyDTO.ActivationKeyPoolDTO akPoolDto : dest.getPools()) {

                        assertNotNull(akPoolDto);

                        if (akPoolDto.getPoolId().equals(akPool.getId())) {
                            assertEquals(akPool.getQuantity(), akPoolDto.getQuantity());
                        }
                    }
                }

                for (ActivationKeyContentOverride akOverride : source.getContentOverrides()) {
                    for (ActivationKeyDTO.ActivationKeyContentOverrideDTO akOverrideDto :
                        dest.getContentOverrides()) {

                        assertNotNull(akOverrideDto);

                        if (akOverrideDto.getName().equals(akOverride.getName())) {
                            assertEquals(akOverrideDto.getContentLabel(), akOverride.getContentLabel());
                            assertEquals(akOverrideDto.getValue(), akOverride.getValue());
                        }
                    }
                }

                Release releaseSource = source.getReleaseVer();
                String releaseDestination = dest.getReleaseVersion();
                if (releaseSource != null) {
                    assertEquals(releaseSource.getReleaseVer(), releaseDestination);
                }
                else {
                    assertNull(releaseDestination);
                }
            }
            else {
                assertNull(dest.getOwner());
                assertNull(dest.getProducts());
                assertNull(dest.getPools());
                assertNull(dest.getContentOverrides());
                assertNull(dest.getReleaseVersion());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
