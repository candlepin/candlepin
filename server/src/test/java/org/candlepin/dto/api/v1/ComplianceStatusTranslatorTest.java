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

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Entitlement;
import org.candlepin.policy.js.compliance.ComplianceReason;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.util.Util;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;




/**
 * Test suite for the ComplianceStatusTranslator class
 */
public class ComplianceStatusTranslatorTest extends
    AbstractTranslatorTest<ComplianceStatus, ComplianceStatusDTO, ComplianceStatusTranslator> {

    protected ComplianceStatusTranslator translator;
    protected ComplianceReasonTranslator reasonTranslator;
    protected EntitlementTranslator entitlementTranslator;

    @Override
    protected ComplianceStatusTranslator initObjectTranslator() {
        this.reasonTranslator = new ComplianceReasonTranslator();
        this.entitlementTranslator = new EntitlementTranslator();

        this.translator = new ComplianceStatusTranslator();
        return this.translator;
    }

    @Override
    protected void initModelTranslator(ModelTranslator translator) {
        translator.registerTranslator(reasonTranslator, ComplianceReason.class, ComplianceReasonDTO.class);
        translator.registerTranslator(entitlementTranslator, Entitlement.class, EntitlementDTO.class);
        translator.registerTranslator(this.translator, ComplianceStatus.class, ComplianceStatusDTO.class);
    }

    @Override
    protected ComplianceStatus initSourceObject() {
        ComplianceStatus source = new ComplianceStatus();

        Set<ComplianceReason> reasons = new HashSet<>();

        for (int i = 0; i < 3; ++i) {
            ComplianceReason reason = new ComplianceReason();
            reason.setKey("test-key-" + i);
            reason.setMessage("test-msg-" + i);

            Map<String, String> attributes = new HashMap<>();
            attributes.put("a1-" + i, "v1-" + i);
            attributes.put("a2-" + i, "v2-" + i);
            attributes.put("a3-" + i, "v3-" + i);

            reasons.add(reason);
        }

        Map<String, DateRange> ranges = new HashMap<>();

        for (int i = 0; i < 3; ++i) {
            DateRange range = new DateRange();

            Calendar sdc = Calendar.getInstance();
            sdc.add(Calendar.HOUR, i);

            range.setStartDate(Util.toDateTime(sdc.getTime()));

            Calendar edc = Calendar.getInstance();
            edc.add(Calendar.HOUR, i);
            edc.add(Calendar.YEAR, 1);

            range.setEndDate(Util.toDateTime(edc.getTime()));

            ranges.put("test_prod-" + i, range);
        }

        Map<String, Set<Entitlement>> compliantProducts = new HashMap<>();
        Map<String, Set<Entitlement>> partiallyCompliantProducts = new HashMap<>();
        Map<String, Set<Entitlement>> partialStacks = new HashMap<>();

        for (int i = 0; i < 3; ++i) {
            compliantProducts.put("p" + i, this.generateEntitlements(3));
            partiallyCompliantProducts.put("p" + (3 + i), this.generateEntitlements(3));
            partialStacks.put("s" + i, this.generateEntitlements(3));
        }

        source.setDate(new Date());
        source.setCompliantUntil(new Date());

        source.getCompliantProducts().putAll(compliantProducts);
        source.getNonCompliantProducts().addAll(Arrays.asList("p1", "p2", "p3"));
        source.getPartiallyCompliantProducts().putAll(partiallyCompliantProducts);
        source.getPartialStacks().putAll(partialStacks);
        source.getProductComplianceDateRanges().putAll(ranges);
        source.setReasons(reasons);

        return source;
    }

    private Set<Entitlement> generateEntitlements(int count) {
        Set<Entitlement> ents = new HashSet<>();

        for (int i = 0; i < count; ++i) {
            Entitlement dto = new Entitlement();
            dto.setId("test-dto-" + i);

            ents.add(dto);
        }

        return ents;
    }

    @Override
    protected ComplianceStatusDTO initDestinationObject() {
        return new ComplianceStatusDTO();
    }

    @Override
    protected void verifyOutput(ComplianceStatus source, ComplianceStatusDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getStatus(), dest.getStatus());
            assertEquals(source.getDate(), Util.toDate(dest.getDate()));
            assertEquals(source.getCompliantUntil(), Util.toDate(dest.getCompliantUntil()));
            assertEquals(source.getNonCompliantProducts(), dest.getNonCompliantProducts());
            assertEquals(source.getProductComplianceDateRanges(), dest.getProductComplianceDateRanges());

            if (childrenGenerated) {
                this.compareEntitlementMaps(source.getCompliantProducts(), dest.getCompliantProducts());
                this.compareEntitlementMaps(source.getPartiallyCompliantProducts(),
                    dest.getPartiallyCompliantProducts());
                this.compareEntitlementMaps(source.getPartialStacks(), dest.getPartialStacks());

                Set<ComplianceReason> reasons = source.getReasons();
                if (reasons != null) {
                    Set<ComplianceReasonDTO> reasonDTOs = dest.getReasons();
                    assertNotNull(reasonDTOs);
                    assertEquals(reasons.size(), reasonDTOs.size());

                    for (ComplianceReason reason : reasons) {
                        if (reason != null) {
                            ComplianceReasonDTO expected = this.reasonTranslator.translate(reason);
                            assertTrue(reasonDTOs.contains(expected));
                        }
                        else {
                            assertTrue(reasonDTOs.contains(null));
                        }
                    }
                }
                else {
                    assertNull(dest.getReasons());
                }
            }
            else {
                assertNull(dest.getCompliantProducts());
                assertNull(dest.getPartiallyCompliantProducts());
                assertNull(dest.getPartialStacks());
                assertNull(dest.getReasons());
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
                            EntitlementDTO expected = this.entitlementTranslator.translate(entitlement);

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
