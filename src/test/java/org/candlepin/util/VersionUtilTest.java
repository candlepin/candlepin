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
package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.Rules;
import org.candlepin.version.VersionUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.PrintStream;
import java.util.Map;


public class VersionUtilTest {

    @AfterEach
    public void tearDown() throws Exception {
        writeoutVersion("${version}", "${release}");
    }

    @Test
    public void normalVersion() throws Exception {
        writeoutVersion("1.3.0", "1");

        Map<String, String> map = VersionUtil.getVersionMap();
        assertEquals("1.3.0", map.get("version"));
        assertEquals("1", map.get("release"));
    }

    @Test
    public void unknown() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("version.properties").toURI()));
        ps.println("corrupted");
        ps.close();

        Map<String, String> map = VersionUtil.getVersionMap();
        assertEquals("Unknown", map.get("version"));
        assertEquals("Unknown", map.get("release"));
    }

    @Test
    public void rulesCompatibility() {
        assertTrue(VersionUtil.getRulesVersionCompatibility("0.4.0", "0.4.0"));
        assertTrue(VersionUtil.getRulesVersionCompatibility("0.4.0", "0.5.1"));
        assertFalse(VersionUtil.getRulesVersionCompatibility("0.4.0", "0.3.99"));
    }

    @Test
    public void rulesCompatibilityComplex() {
        assertFalse(VersionUtil.getRulesVersionCompatibility("0.5.15", "0.5.2"));
        assertFalse(VersionUtil.getRulesVersionCompatibility("0.5.15", "0.5.5.2-1"));
        assertFalse(VersionUtil.getRulesVersionCompatibility("0.5.15", "adf25d9c"));
        assertFalse(VersionUtil.getRulesVersionCompatibility("0.5.15", "0a4d52b0"));
        assertTrue(VersionUtil.getRulesVersionCompatibility("0.5.15", "0.5.15-1hotfix"));
        assertTrue(VersionUtil.getRulesVersionCompatibility("0.5.15", "0.5.20"));
    }

    @Test
    public void rulesCompatibilityVsNull() {
        assertFalse(VersionUtil.getRulesVersionCompatibility("0.5.15", null));
    }

    public static void writeoutVersion(String version, String release) throws Exception {
        PrintStream ps = new PrintStream(new File(Rules.class
            .getClassLoader().getResource("version.properties").toURI()));
        ps.println("version=" + version);
        ps.println("release=" + release);
        ps.close();
    }

}
