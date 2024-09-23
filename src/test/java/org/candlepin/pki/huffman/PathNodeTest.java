/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki.huffman;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PathNodeTest {
    private static NodePair addChild(PathNode target, int id, String name) {
        PathNode childConn = new PathNode(id);
        NodePair child = new NodePair(name, childConn);
        target.addChild(child);
        return child;
    }

    @Test
    public void isEquivalentTo() {
        // Two nodes with different ids, but have children with identical names
        // and connections, regardless of child ordering, are equivalent.
        int id = 0;
        PathNode a = new PathNode(++id);
        addChild(a, ++id, "rhel8");
        addChild(a, ++id, "rhel9");

        PathNode b = new PathNode(++id);
        addChild(b, ++id, "rhel9");
        addChild(b, ++id, "rhel8");

        assertTrue(a.isEquivalentTo(b));
    }

    @Test
    public void isEquivalentToNoChildren() {
        // Two nodes with different ids and no children are equivalent.
        int id = 0;
        PathNode a = new PathNode(++id);
        PathNode b = new PathNode(++id);
        assertTrue(a.isEquivalentTo(b));
    }

    @Test
    public void isEquivalentToSelf() {
        // A node is equivalent to itself.
        int id = 0;
        PathNode a = new PathNode(++id);
        addChild(a, ++id, "rhel8");
        addChild(a, ++id, "rhel9");
        assertTrue(a.isEquivalentTo(a));
    }

    @Test
    public void isEquivalentToDifferentAmountOfChildren() {
        // If the nodes have different child amounts, they aren't equivalent
        int id = 0;
        PathNode a = new PathNode(++id);
        addChild(a, ++id, "rhel8");
        addChild(a, ++id, "rhel9");

        PathNode b = new PathNode(++id);
        addChild(b, ++id, "rhel8");
        addChild(b, ++id, "rhel9");
        addChild(b, ++id, "rhel10");

        assertFalse(a.isEquivalentTo(b));
        // (specially check the inverse holds as well)
        assertFalse(b.isEquivalentTo(a));
    }

    @Test
    public void isEquivalentToUnmatchedChild() {
        // If the nodes have same amount of children, but one child has no
        // match, then the nodes aren't equivalent.
        int id = 0;
        PathNode a = new PathNode(++id);
        addChild(a, ++id, "rhel8");
        addChild(a, ++id, "rhel9");

        PathNode b = new PathNode(++id);
        addChild(b, ++id, "rhel8");
        addChild(b, ++id, "tandy basic");

        assertFalse(a.isEquivalentTo(b));
    }

    @Test
    public void isEquivalentToUnequivalentConnection() {
        // If the nodes have matching-by-name children, but at least one child
        // does not have an equivalent connection to its matches' connection,
        // then the nodes aren't equivalent.
        int id = 0;
        PathNode a = new PathNode(++id);
        addChild(a, ++id, "rhel8");
        NodePair aChild = addChild(a, ++id, "rhel9");
        addChild(aChild.getConnection(), ++id, "iso");

        PathNode b = new PathNode(++id);
        addChild(b, ++id, "rhel8");
        NodePair bChild = addChild(b, ++id, "rhel9");
        addChild(bChild.getConnection(), ++id, "different");

        assertFalse(a.isEquivalentTo(b));
    }

}
