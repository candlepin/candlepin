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
package org.candlepin.dto.api.v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.TestingModules;
import org.candlepin.common.config.Configuration;
import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.util.Util;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.xnap.commons.i18n.I18n;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the ComplianceStatusTranslator class
 */
public class SystemPurposeComplianceStatusTranslatorTest extends
    AbstractTranslatorTest<SystemPurposeComplianceStatus, SystemPurposeComplianceStatusDTO,
    SystemPurposeComplianceStatusTranslator> {

    protected SystemPurposeComplianceStatusTranslator translator;
    protected EntitlementTranslator entitlementTranslator;
    protected PoolTranslator poolTranslator;
    private Injector injector;
    private I18n i18n;

    @Override
    protected SystemPurposeComplianceStatusTranslator initObjectTranslator() {


        Configuration config = mock(Configuration.class);
        injector = Guice.createInjector(
            new TestingModules.MockJpaModule(),
            new TestingModules.StandardTest(config),
            new TestingModules.ServletEnvironmentModule()
        );
        i18n = injector.getInstance(I18n.class);
        this.entitlementTranslator = new EntitlementTranslator();
        this.poolTranslator = new PoolTranslator();

        this.translator = new SystemPurposeComplianceStatusTranslator();
        return this.translator;
    }

    @Override
    protected void initModelTranslator(ModelTranslator translator) {
        translator.registerTranslator(entitlementTranslator, Entitlement.class, EntitlementDTO.class);
        translator.registerTranslator(poolTranslator, Pool.class, PoolDTO.class);
        translator.registerTranslator(this.translator, SystemPurposeComplianceStatus.class,
            SystemPurposeComplianceStatusDTO.class);
    }

    @Override
    protected SystemPurposeComplianceStatus initSourceObject() {
        SystemPurposeComplianceStatus source = new SystemPurposeComplianceStatus(i18n);

        for (int i = 0; i < 3; ++i) {
            source.addNonCompliantAddOn("test-add-on-" + i);
        }

        for (int i = 0; i < 3; ++i) {
            source.addCompliantRole("r" + i, this.generateEntitlement("role", i));
            source.addCompliantAddOn("a" + i, this.generateEntitlement("addOn", i));
            source.addCompliantUsage("u" + i, this.generateEntitlement("usage", i));
            source.addCompliantSLA("a" + i, this.generateEntitlement("SLA", i));
        }

        source.setDate(new Date());
        source.setNonCompliantRole("nonCompliantRole");
        source.setNonCompliantSLA("nonCompliantSLA");
        source.setNonCompliantUsage("nonCompliantUsage");

        return source;
    }

    private Entitlement generateEntitlement(String message, int count) {
        Entitlement ent = new Entitlement();
        Pool pool = new Pool();
        ent.setId(message + count);
        pool.setId("pool-" + message + count);
        ent.setPool(pool);
        return ent;
    }

    @Override
    protected SystemPurposeComplianceStatusDTO initDestinationObject() {
        return new SystemPurposeComplianceStatusDTO();
    }

    @Override
    protected void verifyOutput(SystemPurposeComplianceStatus source, SystemPurposeComplianceStatusDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getStatus(), dest.getStatus());
            assertEquals(source.getDate(), Util.toDate(dest.getDate()));
            assertEquals(source.getNonCompliantRole(), dest.getNonCompliantRole());
            assertEquals(source.getNonCompliantUsage(), dest.getNonCompliantUsage());
            assertEquals(source.getNonCompliantSLA(), dest.getNonCompliantSLA());
            assertEquals(source.getNonCompliantAddOns(), dest.getNonCompliantAddOns());
            assertEquals(source.getReasons(), dest.getReasons());

            if (childrenGenerated) {
                this.compareEntitlementMaps(source.getCompliantRole(), dest.getCompliantRole());
                this.compareEntitlementMaps(source.getCompliantAddOns(), dest.getCompliantAddOns());
                this.compareEntitlementMaps(source.getCompliantUsage(), dest.getCompliantUsage());
                this.compareEntitlementMaps(source.getCompliantSLA(), dest.getCompliantSLA());
            }
            else {
                assertNull(dest.getCompliantRole());
                assertNull(dest.getCompliantAddOns());
                assertNull(dest.getCompliantUsage());
                assertNull(dest.getCompliantSLA());
            }
        }
        else {
            assertNull(dest);
        }
    }

    private void compareEntitlementMaps(Map<String, Set<Entitlement>> src,
        Map<String, Set<EntitlementDTO>> dest) {

        if (src != null) {
            assertNotNull(dest);

            for (Map.Entry<String, Set<Entitlement>> entry : src.entrySet()) {
                assertTrue(dest.containsKey(entry.getKey()));

                Set<Entitlement> srcEnts = entry.getValue();
                Set<EntitlementDTO> destEnts = dest.get(entry.getKey());

                if (srcEnts != null) {
                    assertNotNull(destEnts);
                    assertEquals(srcEnts.size(), destEnts.size());

                    for (Entitlement entitlement : srcEnts) {
                        if (entitlement != null) {
                            EntitlementDTO expected = this.entitlementTranslator.translate(modelTranslator,
                                entitlement);

                            // Since we don't have something to handle nested objects, we need to set the
                            // collections to empty collections manually
                            expected.setCertificates(Collections.emptySet());

                            assertTrue(expected + " is not contained in collection: " + destEnts,
                                destEnts.contains(expected));
                        }
                        else {
                            assertTrue(destEnts.contains(null));
                        }
                    }
                }
                else {
                    assertNull(destEnts);
                }
            }
        }
        else {
            assertNull(dest);
        }
    }
}
