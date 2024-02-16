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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.candlepin.model.dto.Content;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class HuffmanTest {
    private Huffman huffman;

    @BeforeEach
    public void setUp() {
        this.huffman = new Huffman();
    }

    @Test
    public void compareToEquals() {
        PathNode pn = new PathNode(1);
        NodePair np = new NodePair("name", pn);
        NodePair np1 = new NodePair("name", pn);
        assertEquals(0, np.compareTo(np1));
        assertEquals(np, np1);
    }

    @Test
    public void nullCompareTo() {
        PathNode pn = new PathNode(1);
        NodePair np = new NodePair("name", pn);

        assertThrows(NullPointerException.class, () -> np.compareTo(null));
    }

    @Test
    public void nullEquals() {
        PathNode pn = new PathNode(1);
        NodePair np = new NodePair("name", pn);
        assertNotEquals(null, np);
    }

    @Test
    public void otherObjectEquals() {
        PathNode pn = new PathNode(1);
        NodePair np = new NodePair("name", pn);
        assertNotEquals(np, pn);
    }

    @Test
    public void notEqualNodes() {
        PathNode pn = new PathNode(1);
        NodePair np = new NodePair("name", pn);
        NodePair np1 = new NodePair("diff", pn);
        assertTrue(np.compareTo(np1) > 0);
        assertNotEquals(np, np1);
    }

    @Test
    public void testPathTreeCommonHeadAndTail() {
        List<Content> contentList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            org.candlepin.model.dto.Content cont = new org.candlepin.model.dto.Content();
            cont.setPath("/head/neck/shoulders/heart" + i + "/waist" + i + "/leg/foot/heel");
            contentList.add(cont);
        }
        PathNode location = this.huffman.makePathTree(contentList, new PathNode(1L));
        this.huffman.printTree(location, 0);
        assertEquals(1, location.getChildren().size());
        assertEquals("head", location.getChildren().get(0).getName());
        location = location.getChildren().get(0).getConnection();
        assertEquals(1, location.getChildren().size());
        assertEquals("neck", location.getChildren().get(0).getName());
        location = location.getChildren().get(0).getConnection();
        assertEquals(1, location.getChildren().size());
        assertEquals("shoulders", location.getChildren().get(0).getName());
        location = location.getChildren().get(0).getConnection();
        assertEquals(20, location.getChildren().size());

        // find the common footer nodes and make sure they are merged.
        long legId = -1;
        long footId = -1;
        long heelId = -1;
        for (NodePair np : location.getChildren()) {
            // np is a "heart" pair
            assertTrue(np.getName().startsWith("heart"));

            // now waist node
            PathNode waist = np.getConnection();
            assertEquals(1, waist.getChildren().size());
            assertTrue(waist.getChildren().get(0).getName().startsWith("waist"));

            // go to "leg" node
            PathNode leg = waist.getChildren().get(0).getConnection();
            if (legId == -1) {
                legId = leg.getId();
            }
            else {
                assertEquals(leg.getId(), legId);
            }
            assertEquals(1, leg.getChildren().size());
            assertEquals("leg", leg.getChildren().get(0).getName());

            // go to "foot" node
            PathNode foot = leg.getChildren().get(0).getConnection();
            if (footId == -1) {
                footId = foot.getId();
            }
            else {
                assertEquals(foot.getId(), footId);
            }
            assertEquals(1, foot.getChildren().size());
            assertEquals("foot", foot.getChildren().get(0).getName());

            // go to "heel" node
            PathNode heel = foot.getChildren().get(0).getConnection();
            if (heelId == -1) {
                heelId = heel.getId();
            }
            else {
                assertEquals(heel.getId(), heelId);
            }
            assertEquals(1, heel.getChildren().size());
            assertEquals("heel", heel.getChildren().get(0).getName());
        }
    }


    @Test
    public void testPathTreeSortsChildNodesAlphabetically() {
        List<org.candlepin.model.dto.Content> contentList = new ArrayList<>();

        org.candlepin.model.dto.Content contentA = new org.candlepin.model.dto.Content();
        contentA.setPath("/AAA");
        org.candlepin.model.dto.Content contentB = new org.candlepin.model.dto.Content();
        contentB.setPath("/BBB");
        org.candlepin.model.dto.Content contentC = new org.candlepin.model.dto.Content();
        contentC.setPath("/CCC");

        contentList.add(contentB);
        contentList.add(contentC);
        contentList.add(contentA);

        PathNode location = this.huffman.makePathTree(contentList, new PathNode(1));

        assertEquals(3, location.getChildren().size(), 3);
        assertEquals("AAA", location.getChildren().get(0).getName());
        assertEquals("BBB", location.getChildren().get(1).getName());
        assertEquals("CCC", location.getChildren().get(2).getName());
    }

    @Test
    public void testPathDictionary() {
        List<org.candlepin.model.dto.Content> contentList = new ArrayList<>();
        org.candlepin.model.dto.Content cont;

        for (int i = 0; i < 20; i++) {
            cont = new org.candlepin.model.dto.Content();
            cont.setPath("/head/neck/shoulders/heart" + i + "/waist" + i + "/leg/foot/heel");
            contentList.add(cont);
        }

        cont = new org.candlepin.model.dto.Content();
        cont.setPath("/head/neck/shoulders/chest/leg");
        contentList.add(cont);
        cont = new org.candlepin.model.dto.Content();
        cont.setPath("/head/neck/shoulders/chest/foot");
        contentList.add(cont);
        cont = new org.candlepin.model.dto.Content();
        cont.setPath("/head/neck/shoulders/chest/torso/leg");
        contentList.add(cont);

        PathNode location = this.huffman.makePathTree(contentList, new PathNode(1));
        List<String> nodeStrings = this.huffman.orderStrings(location);
        assertEquals(48, nodeStrings.size());

        // frequency sorted
        assertEquals("foot", nodeStrings.get(46));
        assertEquals("leg", nodeStrings.get(47));
    }

    @Test
    public void testHuffNodeTrieCreationAndTreeSearch() {
        String[] paths = {"01110", "01111", "0110", "1110", "1111", "010", "100", "101", "110", "00"};
        List<HuffNode> huffNodes = new ArrayList<>();
        List<Object> members = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Object o = new Object();
            huffNodes.add(new HuffNode(2, o, i));
            members.add(o);
        }
        HuffNode trieParent = this.huffman.makeTrie(huffNodes);
        this.huffman.printTrie(trieParent, 0);
        assertEquals(55, trieParent.getWeight());
        assertEquals(22, trieParent.getLeft().getWeight());
        assertEquals(33, trieParent.getRight().getWeight());

        int idx = 0;
        for (Object o : members) {
            assertEquals(paths[idx], this.huffman.findHuffPath(trieParent, o));
            Object found = this.huffman.findHuffNodeValueByBits(trieParent, paths[idx++]);
            assertEquals(o, found);
        }
    }

    @ParameterizedTest
    @MethodSource("pathTreeCondensationProvider")
    public void testPathTreeCondensation(List<String> paths) {
        List<org.candlepin.model.dto.Content> contentList = new ArrayList<>();
        for (String path : paths) {
            org.candlepin.model.dto.Content content = new org.candlepin.model.dto.Content();
            content.setPath(path);
            contentList.add(content);
        }

        PathNode location = this.huffman.makePathTree(contentList, new PathNode(1));
        for (org.candlepin.model.dto.Content c : contentList) {
            List<String> path = Arrays.asList(c.getPath().split("/"));
            assertTrue(checkPath(location, path.subList(1, path.size())), "failed path " + c.getPath());
        }
    }

    public static Stream<Arguments> pathTreeCondensationProvider() {
        // BZ 2131312
        Arguments block1 = arguments(List.of(
            "/content/dist/rhel/server/6/$releasever/$basearch/satellite/6.0/os",
            "/content/dist/rhel/server/6/$releasever/$basearch/satellite/6.0/source/SRPMS",
            "/content/dist/layered/rhel9/x86_64/sat-client/6/source/SRPMS",
            "/content/beta/layered/rhel8/x86_64/sat-tools/6/source/SRPMS",
            "/content/beta/layered/rhel8/x86_64/sat-tools/6/os",
            "/content/dist/layered/rhel8/x86_64/sat-tools/6.8/os",
            "/content/dist/layered/rhel8/x86_64/sat-client/6/os",
            "/content/dist/layered/rhel9/x86_64/sat-client/6/os",
            "/content/dist/layered/rhel8/x86_64/sat-client/6/source/SRPMS"));
        // add additional blocks if other test scenarios arrive

        return Stream.of(block1);
    }

    boolean checkPath(PathNode location, List<String> path) {
        if (path.isEmpty()) {
            return true;
        }
        for (NodePair pn : location.getChildren()) {
            if (path.get(0).equals(pn.getName())) {
                if (path.size() == 1) {
                    return true;
                }
                return checkPath(pn.getConnection(), path.subList(1, path.size()));
            }
        }
        return false;
    }
}
