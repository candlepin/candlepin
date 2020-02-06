/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.PropertiesFileConfiguration;

import com.google.inject.Module;

import org.junit.Test;

import java.net.URISyntaxException;
import java.util.Set;

public class CustomizableModulesTest {

    @Test
    public void shouldLoadAndParseConfigurationFile() throws Exception {
        Configuration config = new PropertiesFileConfiguration(
            getAbsolutePath("customizable_modules_test.conf"));
        Set<Module> loaded = new CustomizableModules().load(config);

        assertEquals(1, loaded.size());
        assertTrue(loaded.iterator().next() instanceof DummyModuleForTesting);
    }

    // TODO: We should probably be more specific...
    @Test(expected = RuntimeException.class)
    public void shouldFailWhenConfigurationContainsMissingClass()
        throws Exception {

        Configuration config = new PropertiesFileConfiguration(
            getAbsolutePath("customizable_modules_with_missing_class.conf"));

        new CustomizableModules().load(config);
    }

    private String getAbsolutePath(String fileName) throws URISyntaxException {
        return getClass().getResource(fileName).toURI().getPath();
    }
}
