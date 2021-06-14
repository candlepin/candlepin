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
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;

import java.util.HashSet;
import java.util.Set;

/**
 * Test suite for the DistributorVersionTranslator (API) class
 */
public class DistributorVersionTranslatorTest
    extends AbstractTranslatorTest<DistributorVersion, DistributorVersionDTO, DistributorVersionTranslator> {

    protected DistributorVersionTranslator translator = new DistributorVersionTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, DistributorVersion.class,
            DistributorVersionDTO.class);
    }

    @Override
    protected DistributorVersionTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected DistributorVersion initSourceObject() {
        DistributorVersion source = new DistributorVersion();
        source.setId("id");
        source.setName("name");
        source.setDisplayName("displayName");

        Set<DistributorVersionCapability> dvCapabilities = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            DistributorVersionCapability dvCapability = new DistributorVersionCapability();
            dvCapability.setId("id-" + i);
            dvCapability.setName("name-" + i);
            dvCapabilities.add(dvCapability);
        }
        source.setCapabilities(dvCapabilities);

        return source;
    }

    @Override
    protected DistributorVersionDTO initDestinationObject() {
        return new DistributorVersionDTO();
    }

    @Override
    protected void verifyOutput(DistributorVersion source, DistributorVersionDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getDisplayName(), dest.getDisplayName());

            for (DistributorVersionCapability dvcEntity : source.getCapabilities()) {
                boolean verified = false;
                for (DistributorVersionCapabilityDTO dvcDTO :
                    dest.getCapabilities()) {

                    assertNotNull(dvcDTO);
                    assertNotNull(dvcDTO.getId());

                    if (dvcDTO.getId().equals(dvcEntity.getId())) {
                        assertEquals(dvcDTO.getName(), dvcEntity.getName());
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
