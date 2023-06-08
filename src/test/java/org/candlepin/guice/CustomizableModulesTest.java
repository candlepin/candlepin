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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;

import com.google.inject.Module;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

public class CustomizableModulesTest {

    @Test
    public void shouldLoadAndParseConfigurationFile() {
        Configuration config = TestConfig.custom(Map.of(
            "module.config.dummy_module", "org.candlepin.guice.DummyModuleForTesting"
        ));

        Set<Module> loaded = new CustomizableModules().load(config);

        assertEquals(1, loaded.size());
        assertTrue(loaded.iterator().next() instanceof DummyModuleForTesting);
    }

    @Test
    public void shouldFailWhenConfigurationContainsMissingClass() {
        Configuration config = TestConfig.custom(Map.of(
            "module.config.broken_module", "org.candlepin.guice.NonExistentModule"
        ));

        // TODO: We should probably be more specific...
        assertThrows(RuntimeException.class, () -> new CustomizableModules().load(config));
    }

}
