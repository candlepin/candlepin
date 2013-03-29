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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.config.Config;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.util.X509V3ExtensionUtil.NodePair;
import org.candlepin.util.X509V3ExtensionUtil.PathNode;
import org.junit.Test;


/**
 * X509V3ExtensionUtilTest
 */
public class X509V3ExtensionUtilTest {

    @Test
    public void compareToEquals() {
        Config config = mock(Config.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        X509V3ExtensionUtil util = new X509V3ExtensionUtil(config, ec);
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        NodePair np1 = new NodePair("name", pn);
        assertEquals(0, np.compareTo(np1));
        assertEquals(np, np1);
    }

    @Test(expected = NullPointerException.class)
    public void nullCompareTo() {
        Config config = mock(Config.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        X509V3ExtensionUtil util = new X509V3ExtensionUtil(config, ec);
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        assertEquals(1, np.compareTo(null));
    }

    @Test
    public void nullEquals() {
        Config config = mock(Config.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        X509V3ExtensionUtil util = new X509V3ExtensionUtil(config, ec);
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        assertFalse(np.equals(null));
    }

    @Test
    public void otherObjectEquals() {
        Config config = mock(Config.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        X509V3ExtensionUtil util = new X509V3ExtensionUtil(config, ec);
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        assertFalse(np.equals(pn));
    }

    @Test
    public void notEqualNodes() {
        Config config = mock(Config.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        X509V3ExtensionUtil util = new X509V3ExtensionUtil(config, ec);
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        NodePair np1 = new NodePair("diff", pn);
        assertTrue(np.compareTo(np1) > 0);
        assertFalse(np.equals(np1));
    }
}
