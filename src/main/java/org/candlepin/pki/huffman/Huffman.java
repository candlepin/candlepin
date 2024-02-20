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
package org.candlepin.pki.huffman;

import org.candlepin.model.dto.Content;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;


public class Huffman {
    private static final Logger log = LoggerFactory.getLogger(Huffman.class);
    private static final Object END_NODE = new Object();
    private static final boolean TREE_DEBUG = false;

    private long pathNodeId = 0;
    private long huffNodeId = 0;

    public byte[] retrieveContentValue(List<Content> contentList) throws IOException {
        PathNode treeRoot = makePathTree(contentList, new PathNode(this.pathNodeId++));
        List<String> nodeStrings = orderStrings(treeRoot);
        if (nodeStrings.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        List<HuffNode> stringHuffNodes = getStringNodeList(nodeStrings);
        HuffNode stringTrieParent = makeTrie(stringHuffNodes);
        data.write(byteProcess(nodeStrings));

        List<PathNode> orderedNodes = orderNodes(treeRoot);
        List<HuffNode> pathNodeHuffNodes = getPathNodeNodeList(orderedNodes);
        HuffNode pathNodeTrieParent = makeTrie(pathNodeHuffNodes);
        data.write(makeNodeDictionary(stringTrieParent,
            pathNodeTrieParent, orderedNodes));

        return data.toByteArray();
    }

    public PathNode makePathTree(List<Content> contents, PathNode parent) {
        PathNode endMarker = new PathNode(this.pathNodeId++);
        for (Content c : contents) {
            String path = c.getPath();

            if (TREE_DEBUG) {
                log.debug(path);
            }
            StringTokenizer st = new StringTokenizer(path, "/");
            makePathForURL(st, parent, endMarker);
        }
        if (TREE_DEBUG) {
            printTree(parent, 0);
        }
        condenseSubTreeNodes(endMarker);
        if (TREE_DEBUG) {
            printTree(parent, 0);
        }
        return parent;
    }

    public void printTree(PathNode pn, int tab) {
        StringBuilder nodeRep = new StringBuilder();
        nodeRep.append("  ".repeat(Math.max(0, tab + 1)));
        nodeRep.append("Node [");
        nodeRep.append(pn.getId());
        nodeRep.append("]");

        for (PathNode parent : pn.getParents()) {
            nodeRep.append(" ^ [");
            nodeRep.append(parent.getId());
            nodeRep.append("]");
        }
        for (NodePair cp : pn.getChildren()) {
            nodeRep.append(" v [");
            nodeRep.append(cp.getName());
            nodeRep.append(" {");
            nodeRep.append(cp.getConnection().getId());
            nodeRep.append("} ]");
        }
        log.debug("{}", nodeRep);
        for (NodePair cp : pn.getChildren()) {
            printTree(cp.getConnection(), tab + 1);
        }
    }

    public void printTrie(HuffNode hn, int tab) {
        StringBuilder nodeRep = new StringBuilder();
        nodeRep.append("  ".repeat(Math.max(0, tab + 1)));
        nodeRep.append("Node [");
        nodeRep.append(hn.getId());
        nodeRep.append("]");

        nodeRep.append(", Weight [");
        nodeRep.append(hn.getWeight());
        nodeRep.append("]");

        nodeRep.append(", Value = [");
        nodeRep.append(hn.getValue());
        nodeRep.append("]");

        log.debug("{}", nodeRep);
        if (hn.getLeft() != null) {
            printTrie(hn.getLeft(), tab + 1);
        }
        if (hn.getRight() != null) {
            printTrie(hn.getRight(), tab + 1);
        }
    }

    private void makePathForURL(StringTokenizer st, PathNode parent, PathNode endMarker) {
        if (st.hasMoreTokens()) {
            String childVal = st.nextToken();
            if (childVal.isEmpty()) {
                return;
            }
            boolean isNew = true;
            for (NodePair child : parent.getChildren()) {
                if (child.getName().equals(childVal) &&
                    !child.getConnection().equals(endMarker)) {
                    makePathForURL(st, child.getConnection(), endMarker);
                    isNew = false;
                }
            }
            if (isNew) {
                PathNode next;
                if (st.hasMoreTokens()) {
                    next = new PathNode(this.pathNodeId++);
                    parent.addChild(new NodePair(childVal, next));
                    next.addParent(parent);
                    makePathForURL(st, next, endMarker);
                }
                else {
                    parent.addChild(new NodePair(childVal, endMarker));
                    if (!endMarker.getParents().contains(parent)) {
                        endMarker.addParent(parent);
                    }
                }
            }
        }
    }

    private void condenseSubTreeNodes(PathNode location) {
        // "equivalent" parents are merged
        List<PathNode> parentResult = new ArrayList<>(location.getParents());
        for (PathNode parent1 : location.getParents()) {
            if (!parentResult.contains(parent1)) {
                continue;
            }
            for (PathNode parent2 : location.getParents()) {
                if (!parentResult.contains(parent2) ||
                    parent2.getId() == parent1.getId()) {
                    continue;
                }
                if (parent1.isEquivalentTo(parent2)) {
                    // we merge them into smaller Id
                    PathNode merged = parent1.getId() < parent2.getId() ?
                        parent1 : parent2;
                    PathNode toRemove = parent1.getId() < parent2.getId() ?
                        parent2 : parent1;

                    // track down the name of the string in the grandparent
                    //  that points to parent
                    String name = "";
                    PathNode oneParent = toRemove.getParents().get(0);
                    for (NodePair child : oneParent.getChildren()) {
                        if (child.getConnection().getId() == toRemove.getId()) {
                            name = child.getName();
                            break;
                        }
                    }

                    // copy grandparents to merged parent node.
                    List<PathNode> movingParents = toRemove.getParents();
                    merged.addParents(movingParents);

                    // all grandparents with name now point to merged node
                    for (PathNode pn : toRemove.getParents()) {
                        for (NodePair child : pn.getChildren()) {
                            if (child.getName().equals(name) &&
                                child.getConnection().isEquivalentTo(merged)) {
                                child.setConnection(merged);
                            }
                        }
                    }
                    parentResult.remove(toRemove);
                }
            }
        }
        location.setParents(parentResult);
        for (PathNode pn : location.getParents()) {
            condenseSubTreeNodes(pn);
        }
    }

    public List<String> orderStrings(PathNode parent) {
        List<String> parts = new ArrayList<>();
        // walk tree to make string map
        Map<String, Integer> segments = new HashMap<>();
        Set<PathNode> nodes = new HashSet<>();
        buildSegments(segments, nodes, parent);
        for (Entry<String, Integer> entry : segments.entrySet()) {
            String part = entry.getKey();
            if (!part.isEmpty()) {
                int count = entry.getValue();
                if (parts.isEmpty()) {
                    parts.add(part);
                }
                else {
                    int pos = parts.size();
                    for (int i = 0; i < parts.size(); i++) {
                        if (count < segments.get(parts.get(i))) {
                            pos = i;
                            break;
                        }
                    }
                    parts.add(pos, part);
                }
            }
        }
        if (TREE_DEBUG) {
            log.debug("Parts List: {}", parts);
        }
        return parts;
    }

    private void buildSegments(Map<String, Integer> segments,
        Set<PathNode> nodes, PathNode parent) {
        if (!nodes.contains(parent)) {
            nodes.add(parent);
            for (NodePair np : parent.getChildren()) {
                Integer count = segments.get(np.getName());
                if (count == null) {
                    count = 0;
                }
                segments.put(np.getName(), ++count);
                buildSegments(segments, nodes, np.getConnection());
            }
        }
    }

    private List<PathNode> orderNodes(PathNode treeRoot) {
        List<PathNode> result = new ArrayList<>();

        // walk tree to make string map
        Set<PathNode> nodes = getPathNodes(treeRoot);
        for (PathNode pn : nodes) {
            int count = pn.getParents().size();
            if (nodes.isEmpty()) {
                nodes.add(pn);
            }
            else {
                int pos = result.size();
                for (int i = 0; i < result.size(); i++) {
                    if (count <= result.get(i).getParents().size()) {
                        pos = i;
                        break;
                    }
                    if (count == result.get(i).getParents().size()) {
                        if (pn.getId() < result.get(i).getId()) {
                            pos = i;
                        }
                        else {
                            pos = i + 1;
                        }
                        break;
                    }
                }
                result.add(pos, pn);
            }
        }
        // single node plus term node. We need to have one more for huffman trie
        if (result.size() == 2) {
            result.add(new PathNode(this.pathNodeId++));
        }
        if (TREE_DEBUG) {
            log.debug("{}", result);
        }
        return result;
    }

    private Set<PathNode> getPathNodes(PathNode treeRoot) {
        Set<PathNode> nodes = new HashSet<>();
        nodes.add(treeRoot);
        for (NodePair np : treeRoot.getChildren()) {
            nodes.addAll(getPathNodes(np.getConnection()));
        }
        return nodes;
    }

    private byte[] makeNodeDictionary(HuffNode stringParent,
        HuffNode pathNodeParent, List<PathNode> pathNodes) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int nodeSize = pathNodes.size();
        if (nodeSize > 127) {
            ByteArrayOutputStream countBaos = new ByteArrayOutputStream();
            boolean start = false;
            for (byte b : toByteArray(nodeSize)) {
                if (b != 0 || start) {
                    countBaos.write(b);
                    start = true;
                }
            }
            baos.write(128 + countBaos.size());
            countBaos.close();
            baos.write(countBaos.toByteArray());
        }
        else {
            baos.write(nodeSize);
        }
        StringBuilder bits = new StringBuilder();
        String endNodeLocation = findHuffPath(stringParent, END_NODE);
        for (PathNode pn : pathNodes) {
            for (NodePair np : pn.getChildren()) {
                bits.append(findHuffPath(stringParent, np.getName()));
                bits.append(findHuffPath(pathNodeParent, np.getConnection()));
            }
            bits.append(endNodeLocation);
            while (bits.length() >= 8) {
                int next = 0;
                for (int i = 0; i < 8; i++) {
                    next = (byte) next << 1;
                    if (bits.charAt(i) == '1') {
                        next++;
                    }
                }
                baos.write(next);
                bits.delete(0, 8);
            }
        }

        if (!bits.isEmpty()) {
            int next = 0;
            for (int i = 0; i < 8; i++) {
                next = (byte) next << 1;
                if (i < bits.length() && bits.charAt(i) == '1') {
                    next++;
                }
            }
            baos.write(next);
        }
        byte[] result = baos.toByteArray();
        if (TREE_DEBUG) {
            ByteArrayInputStream bais = new ByteArrayInputStream(result);
            int value = bais.read();
            while (value != -1) {
                log.debug("{}", value);
                value = bais.read();
            }
        }
        baos.close();
        return result;
    }

    private byte[] toByteArray(int value) {
        return new byte[]{
            (byte) (value >> 24),
            (byte) (value >> 16),
            (byte) (value >> 8),
            (byte) value};
    }

    public String findHuffPath(HuffNode trie, Object need) {
        HuffNode left = trie.getLeft();
        HuffNode right = trie.getRight();
        if (left != null && left.getValue() != null) {
            if (need.equals(left.getValue())) {
                return "0";
            }
        }
        if (right != null && right.getValue() != null) {
            if (need.equals(right.getValue())) {
                return "1";
            }
        }
        if (left != null) {
            String leftPath = findHuffPath(left, need);
            if (!leftPath.isEmpty()) {
                return "0" + leftPath;
            }
        }
        if (right != null) {
            String rightPath = findHuffPath(right, need);
            if (!rightPath.isEmpty()) {
                return "1" + rightPath;
            }
        }
        return "";
    }

    private byte[] byteProcess(List<String> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeflaterOutputStream dos = new DeflaterOutputStream(baos,
            new Deflater(Deflater.BEST_COMPRESSION));
        for (String segment : entries) {
            dos.write(segment.getBytes(StandardCharsets.UTF_8));
            dos.write("\0".getBytes(StandardCharsets.UTF_8));
        }
        dos.finish();
        dos.close();
        return baos.toByteArray();
    }

    private List<HuffNode> getStringNodeList(List<String> pathStrings) {
        List<HuffNode> nodes = new ArrayList<>();
        int idx = 1;
        for (String part : pathStrings) {
            nodes.add(new HuffNode(this.huffNodeId++, part, idx++));
        }
        nodes.add(new HuffNode(this.huffNodeId++, END_NODE, idx));
        return nodes;
    }

    private List<HuffNode> getPathNodeNodeList(List<PathNode> pathNodes) {
        List<HuffNode> nodes = new ArrayList<>();
        int idx = 0;
        for (PathNode pn : pathNodes) {
            nodes.add(new HuffNode(this.huffNodeId++, pn, idx++));
        }
        return nodes;
    }

    public HuffNode makeTrie(List<HuffNode> nodesList) {
        // drop the first node if path node value, it is not needed
        if (nodesList.get(0).getValue() instanceof PathNode) {
            nodesList.remove(0);
        }
        while (nodesList.size() > 1) {
            int node1 = findSmallest(-1, nodesList);
            int node2 = findSmallest(node1, nodesList);
            HuffNode hn1 = nodesList.get(node1);
            HuffNode hn2 = nodesList.get(node2);
            HuffNode merged = HuffNode.merge(this.huffNodeId++, hn1, hn2);
            nodesList.remove(hn1);
            nodesList.remove(hn2);
            nodesList.add(merged);
        }
        if (TREE_DEBUG) {
            printTrie(nodesList.get(0), 0);
        }
        return nodesList.get(0);
    }

    private int findSmallest(int exclude, List<HuffNode> nodes) {
        int smallest = -1;
        for (int index = 0; index < nodes.size(); index++) {
            if (index == exclude) {
                continue;
            }
            if (smallest == -1 || nodes.get(index).getWeight() <
                nodes.get(smallest).getWeight()) {
                smallest = index;
            }
        }
        return smallest;
    }

    public List<String> hydrateContentPackage(byte[] payload) throws IOException {
        List<HuffNode> pathDictionary = new ArrayList<>();
        List<HuffNode> nodeDictionary = new ArrayList<>();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Inflater i = new Inflater();
        InflaterOutputStream ios = new InflaterOutputStream(baos, i);
        ios.write(payload);
        ios.finish();
        long read = i.getBytesRead();

        StringBuilder name = new StringBuilder();
        int weight = 1;
        for (byte b : baos.toByteArray()) {
            if (b == '\0') {
                pathDictionary.add(new HuffNode(this.huffNodeId++, name.toString(), weight++));
                name = new StringBuilder();
            }
            else {
                name.append((char) b);
            }
        }

        pathDictionary.add(new HuffNode(this.huffNodeId++, END_NODE, weight));
        List<HuffNode> triePathDictionary = new ArrayList<>(pathDictionary);
        HuffNode pathTrie = makeTrie(triePathDictionary);

        StringBuilder nodeBits = new StringBuilder();
        ByteArrayInputStream bais = new ByteArrayInputStream(payload, (int) read,
            (int) (payload.length - read));

        int value = bais.read();
        // check for size bits
        int nodeCount = value;
        if (value > 127) {
            int length = value - 128;
            int total = 0;

            if (length > 0) {
                byte[] count = new byte[length];
                int offset = 0;

                do {
                    int bytesRead = bais.read(count, offset, length - offset);

                    if (bytesRead != -1) {
                        for (; bytesRead > 0; --bytesRead) {
                            total = (total << 8) | (count[offset++] & 0xFF);
                        }
                    }
                    else {
                        break;
                    }
                }
                while (offset < length);
            }

            nodeCount = total;
        }

        value = bais.read();
        while (value != -1) {
            String someBits = Integer.toString(value, 2);
            nodeBits.append("0".repeat(Math.max(0, 8 - someBits.length())));
            nodeBits.append(someBits);
            value = bais.read();
        }

        for (int j = 0; j < nodeCount; j++) {
            nodeDictionary.add(new HuffNode(this.huffNodeId++, new PathNode(this.pathNodeId++), j));
        }

        List<HuffNode> trieNodeDictionary = new ArrayList<>(nodeDictionary);
        HuffNode nodeTrie = makeTrie(trieNodeDictionary);

        // populate the PathNodes so we can rebuild the cool url tree
        Set<PathNode> pathNodes =  populatePathNodes(nodeDictionary, pathTrie, nodeTrie, nodeBits);
        // find the root, he has no parents. He does have children
        // added child check because we have a blank placeholder node for the single segment case
        PathNode root = null;
        for (PathNode pn : pathNodes) {
            if (pn.getParents().isEmpty() && !pn.getChildren().isEmpty()) {
                root = pn;
                break;
            }
        }
        // time to make the doughnuts
        List<String> urls = new ArrayList<>();
        StringBuilder aPath = new StringBuilder();
        makeURLs(root, urls, aPath);
        return urls;
    }

    public Object findHuffNodeValueByBits(HuffNode trie, String bits) {
        HuffNode left = trie.getLeft();
        HuffNode right = trie.getRight();

        if (bits.isEmpty()) {
            return trie.getValue();
        }

        char bit = bits.charAt(0);
        if (bit == '0') {
            if (left == null) {
                throw new RuntimeException("Encoded path not in trie");
            }
            return findHuffNodeValueByBits(left, bits.substring(1));
        }
        else if (bit == '1') {
            if (right == null) {
                throw new RuntimeException("Encoded path not in trie");
            }
            return findHuffNodeValueByBits(right, bits.substring(1));
        }
        return null;
    }

    private Set<PathNode> populatePathNodes(List<HuffNode> nodeDictionary,
        HuffNode pathTrie, HuffNode nodeTrie, StringBuilder nodeBits) {
        Set<PathNode> pathNodes = new HashSet<>();
        for (HuffNode node : nodeDictionary) {
            pathNodes.add((PathNode) node.getValue());
            boolean stillNode = true;
            while (stillNode) {
                // get first child name
                // if its END_NODE we are done
                String nameValue = null;
                StringBuilder nameBits = new StringBuilder();
                while (nameValue == null && stillNode) {
                    nameBits.append(nodeBits.charAt(0));
                    nodeBits.deleteCharAt(0);
                    Object lookupValue = findHuffNodeValueByBits(pathTrie,
                        nameBits.toString());
                    if (lookupValue != null) {
                        if (lookupValue.equals(END_NODE)) {
                            stillNode = false;
                            break;
                        }
                        nameValue = (String) lookupValue;
                    }
                    if (nodeBits.isEmpty()) {
                        stillNode = false;
                    }
                }

                PathNode nodeValue = null;
                StringBuilder pathBits = new StringBuilder();
                while (nodeValue == null && stillNode) {
                    pathBits.append(nodeBits.charAt(0));
                    nodeBits.deleteCharAt(0);
                    PathNode lookupValue = (PathNode) findHuffNodeValueByBits(nodeTrie,
                        pathBits.toString());
                    if (lookupValue != null) {
                        nodeValue = lookupValue;
                        nodeValue.addParent((PathNode) node.getValue());
                        ((PathNode) node.getValue()).addChild(
                            new NodePair(nameValue, nodeValue));
                    }
                    if (nodeBits.isEmpty()) {
                        stillNode = false;
                    }
                }
            }
        }
        return pathNodes;
    }

    private void makeURLs(PathNode root, List<String> urls, StringBuilder aPath) {
        if (root == null) {
            // if no PathNode, we just bail. No need to cause an NPE.
            return;
        }

        if (root.getChildren().isEmpty()) {
            urls.add(aPath.toString());
        }

        for (NodePair child : root.getChildren()) {
            StringBuilder childPath = new StringBuilder(aPath.substring(0));
            childPath.append("/");
            childPath.append(child.getName());
            makeURLs(child.getConnection(), urls, childPath);
        }
    }
}
