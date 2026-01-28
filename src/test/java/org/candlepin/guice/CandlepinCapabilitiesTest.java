/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.guice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Test class for CandlepinCapabilities
 */
public class CandlepinCapabilitiesTest {

    private static final Set<String> DEFAULT_CAPABILITIES = Set.of(
        "cores", "ram", "instance_multiplier", "derived_product", "cert_v3",
        "guest_limit", "vcpu", "hypervisors_async", "storage_band", "remove_by_pool_id",
        "batch_bind", "org_level_content_access", "syspurpose", "hypervisors_heartbeat",
        "multi_environment", "typed_environments"
    );

    @AfterEach
    public void tearDown() {
        // Reset capabilities to defaults after each test
        CandlepinCapabilities.setCapabilities(null);
    }

    @Test
    public void testDefaultConstructorCreatesDefaultCapabilities() {
        CandlepinCapabilities capabilities = new CandlepinCapabilities();

        assertThat(capabilities)
            .hasSize(DEFAULT_CAPABILITIES.size())
            .containsAll(DEFAULT_CAPABILITIES);
    }

    @Test
    public void testConstructorWithSpecificCapabilities() {
        CandlepinCapabilities capabilities = new CandlepinCapabilities("cap1", "cap2", "cap3");

        assertThat(capabilities)
            .hasSize(3)
            .contains("cap1", "cap2", "cap3");
    }

    @Test
    public void testConstructorWithNullCapabilities() {
        CandlepinCapabilities capabilities = new CandlepinCapabilities((String[]) null);

        assertThat(capabilities)
            .isEmpty();
    }

    @Test
    public void testConstructorWithEmptyCapabilities() {
        CandlepinCapabilities capabilities = new CandlepinCapabilities(new String[0]);

        assertThat(capabilities)
            .isEmpty();
    }

    @Test
    public void testGetCapabilitiesReturnsSingletonInstance() {
        CandlepinCapabilities first = CandlepinCapabilities.getCapabilities();
        CandlepinCapabilities second = CandlepinCapabilities.getCapabilities();

        assertThat(first)
            .isNotNull()
            .isSameAs(second);
    }

    @Test
    public void testSetCapabilitiesCopiesProvidedCapabilities() {
        CandlepinCapabilities custom = new CandlepinCapabilities("custom1", "custom2");

        CandlepinCapabilities.setCapabilities(custom);

        CandlepinCapabilities result = CandlepinCapabilities.getCapabilities();
        assertThat(result)
            .hasSize(2)
            .contains("custom1", "custom2")
            .doesNotContainAnyElementsOf(DEFAULT_CAPABILITIES);
    }

    @Test
    public void testSetCapabilitiesWithNullResetsToDefaults() {
        // First set custom capabilities
        CandlepinCapabilities custom = new CandlepinCapabilities("custom1", "custom2");
        CandlepinCapabilities.setCapabilities(custom);

        // Verify custom capabilities are set
        assertThat(CandlepinCapabilities.getCapabilities())
            .hasSize(2);

        // Reset to defaults
        CandlepinCapabilities.setCapabilities(null);

        // Verify defaults are restored
        assertThat(CandlepinCapabilities.getCapabilities())
            .hasSize(DEFAULT_CAPABILITIES.size())
            .containsAll(DEFAULT_CAPABILITIES);
    }

    @Test
    public void testSetCapabilitiesReturnsSameInstance() {
        CandlepinCapabilities before = CandlepinCapabilities.getCapabilities();

        CandlepinCapabilities custom = new CandlepinCapabilities("custom1");
        CandlepinCapabilities.setCapabilities(custom);

        CandlepinCapabilities after = CandlepinCapabilities.getCapabilities();

        assertThat(before)
            .isSameAs(after);
    }

    @Test
    public void testSetCapabilitiesWithEmptyCapabilities() {
        CandlepinCapabilities empty = new CandlepinCapabilities(new String[0]);

        CandlepinCapabilities.setCapabilities(empty);

        assertThat(CandlepinCapabilities.getCapabilities())
            .isEmpty();
    }

    @Test
    public void testCapabilitiesAreMutable() {
        CandlepinCapabilities.setCapabilities(null); // Reset to defaults

        CandlepinCapabilities capabilities = CandlepinCapabilities.getCapabilities();
        capabilities.add("new_capability");

        assertThat(CandlepinCapabilities.getCapabilities())
            .contains("new_capability");
    }
}
