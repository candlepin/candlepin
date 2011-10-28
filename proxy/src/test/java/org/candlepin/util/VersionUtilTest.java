/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import static junit.framework.Assert.assertTrue;

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
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("candlepin_info.properties").toURI()));
        ps.println("version=1.3.0");
        ps.println("release=1");
        ps.close();

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
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("candlepin_info.properties").toURI()));
        ps.println("version=${version}");
        ps.println("release=${release}");
        ps.close();
    }

}
