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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import org.candlepin.model.Rules;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.PrintStream;
import java.util.Map;

/**
 * VersionUtilTest
 */
public class VersionUtilTest {

    @Test
    public void normalVersion() throws Exception {
        writeoutVersion("1.3.0", "1");

        Map<String, String> map = VersionUtil.getVersionMap();
        assertTrue("1.3.0".equals(map.get("version")));
        assertTrue("1".equals(map.get("release")));
    }

    @Test
    public void unknown() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("candlepin_info.properties").toURI()));
        ps.println("corrupted");
        ps.close();

        Map<String, String> map = VersionUtil.getVersionMap();
        assertTrue("Unknown".equals(map.get("version")));
        assertTrue("Unknown".equals(map.get("release")));
    }

    @After
    public void tearDown() throws Exception {
        writeoutVersion("${version}", "${release}");
    }

    @Test
    // moved tests from old VersionTest to here
    public void rulesCompatibility() throws Exception {
        writeoutVersion("0.4.0", "1");
        assertTrue(VersionUtil.getRulesVersionCompatibility("0.4.0"));
        assertTrue(VersionUtil.getRulesVersionCompatibility("0.5.1"));
        assertFalse(VersionUtil.getRulesVersionCompatibility("0.3.99"));
    }

    @Test
    public void rulesCompatibilityComplex() throws Exception {
        writeoutVersion("0.5.15", "1");
        assertFalse(VersionUtil.getRulesVersionCompatibility("0.5.2"));
        assertFalse(VersionUtil.getRulesVersionCompatibility("0.5.5.2-1"));
        assertFalse(VersionUtil.getRulesVersionCompatibility("adf25d9c"));
        assertFalse(VersionUtil.getRulesVersionCompatibility("0a4d52b0"));
        assertTrue(VersionUtil.getRulesVersionCompatibility("0.5.15-1hotfix"));
        assertTrue(VersionUtil.getRulesVersionCompatibility("0.5.20"));
    }

    @Test
    public void rulesCompatibilityVsNull() throws Exception {
        writeoutVersion("0.5.15", "1");
        assertFalse(VersionUtil.getRulesVersionCompatibility(null));
    }

    public static void writeoutVersion(String version, String release) throws Exception {
        PrintStream ps = new PrintStream(new File(new Rules().getClass()
            .getClassLoader().getResource("candlepin_info.properties").toURI()));
        ps.println("version=" + version);
        ps.println("release=" + release);
        ps.close();
    }

}
