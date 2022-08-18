/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.data.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;


public class X509HuffmanDecodeUtil {

    private static final Logger log = LoggerFactory.getLogger(X509HuffmanDecodeUtil.class);
    private static final Object END_NODE = new Object();

    private long pathNodeId = 0;
    private long huffNodeId = 0;


    public X509HuffmanDecodeUtil() {
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
            HuffNode merged = mergeNodes(hn1, hn2);
            nodesList.remove(hn1);
            nodesList.remove(hn2);
            nodesList.add(merged);
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

    private HuffNode mergeNodes(HuffNode left, HuffNode right) {
        return new HuffNode(null, left.weight + right.weight, left, right);
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

        String name = "";
        int weight = 1;
        for (byte b : baos.toByteArray()) {
            if (b == '\0') {
                pathDictionary.add(new HuffNode(name, weight++));
                name = "";
            }
            else {
                name += (char) b;
            }
        }

        pathDictionary.add(new HuffNode(END_NODE, weight));
        List<HuffNode> triePathDictionary = new ArrayList<>(pathDictionary);
        HuffNode pathTrie = makeTrie(triePathDictionary);

        StringBuffer nodeBits = new StringBuffer();
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
            for (int pad = 0; pad < 8 - someBits.length(); pad++) {
                nodeBits.append("0");
            }
            nodeBits.append(someBits);
            value = bais.read();
        }

        for (int j = 0; j < nodeCount; j++) {
            nodeDictionary.add(new HuffNode(new PathNode(), j));
        }

        List<HuffNode> trieNodeDictionary = new ArrayList<>(nodeDictionary);
        HuffNode nodeTrie = makeTrie(trieNodeDictionary);

        // populate the PathNodes so we can rebuild the cool url tree
        Set<PathNode> pathNodes =  populatePathNodes(nodeDictionary, pathTrie, nodeTrie, nodeBits);
        // find the root, he has no parents. He does have children
        // added child check because we have a blank placeholder node for the single segment case
        PathNode root = null;
        for (PathNode pn : pathNodes) {
            if (pn.getParents().size() == 0 && pn.getChildren().size() > 0) {
                root = pn;
                break;
            }
        }
        // time to make the doughnuts
        List<String> urls = new ArrayList<>();
        StringBuffer aPath = new StringBuffer();
        makeURLs(root, urls, aPath);
        return urls;
    }

    public Object findHuffNodeValueByBits(HuffNode trie, String bits) {
        HuffNode left = trie.getLeft();
        HuffNode right = trie.getRight();

        if (bits.length() == 0) {
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
        HuffNode pathTrie, HuffNode nodeTrie, StringBuffer nodeBits) {
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
                    if (nodeBits.length() == 0) {
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
                    if (nodeBits.length() == 0) {
                        stillNode = false;
                    }
                }
            }
        }
        return pathNodes;
    }

    private void makeURLs(PathNode root, List<String> urls, StringBuffer aPath) {
        if (root == null) {
            // if no PathNode, we just bail. No need to cause an NPE.
            return;
        }

        if (root.getChildren().size() == 0) {
            urls.add(aPath.toString());
        }

        for (NodePair child : root.getChildren()) {
            StringBuffer childPath = new StringBuffer(aPath.substring(0));
            childPath.append("/");
            childPath.append(child.getName());
            makeURLs(child.getConnection(), urls, childPath);
        }
    }

    public class HuffNode {
        private long id = 0;
        private Object value = null;
        private int weight = 0;
        private HuffNode left = null;
        private HuffNode right = null;

        public HuffNode(Object value, int weight, HuffNode left, HuffNode right) {
            this.value = value;
            this.weight = weight;
            this.left = left;
            this.right = right;
            this.id = huffNodeId++;
        }
        public HuffNode(Object value, int weight) {
            this.value = value;
            this.weight = weight;
            this.id = huffNodeId++;
        }

        public Object getValue() {
            return this.value;
        }

        public int getWeight() {
            return this.weight;
        }

        public HuffNode getLeft() {
            return this.left;
        }

        public HuffNode getRight() {
            return this.right;
        }

        long getId() {
            return this.id;
        }

        public String toString() {
            return "ID: " + id +
                   ", Value: " + value +
                   ", Weight: " + weight +
                   ", Left: " + left +
                   ", Right: " + right;
        }
    }

    public class PathNode {
        private long id = 0;
        private List<NodePair> children = new ArrayList<>();
        private List<PathNode> parents = new ArrayList<>();

        public PathNode() {
            this.id = pathNodeId++;
        }

        public long getId() {
            return id;
        }

        void addChild(NodePair cp) {
            this.children.add(cp);
        }

        void addParent(PathNode cp) {
            if (!parents.contains(cp)) {
                this.parents.add(cp);
            }
        }

        public List<NodePair> getChildren() {
            Collections.sort(this.children);
            return this.children;
        }

        List<PathNode> getParents() {
            return this.parents;
        }

        void setParents(List<PathNode> parents) {
            this.parents = parents;
        }

        void addParents(List<PathNode> parents) {
            for (PathNode pn : parents) {
                addParent(pn);
            }
        }

        boolean isEquivalentTo(PathNode that) {
            if (this.getId() == that.getId()) {
                return true;
            }
            // same number of children with the same names for child nodes
            if (this.getChildren().size() != that.getChildren().size()) {
                return false;
            }
            for (NodePair thisnp : this.getChildren()) {
                boolean found = false;
                for (NodePair thatnp : that.getChildren()) {
                    if (thisnp.getName().equals(thatnp.getName())) {
                        if (thisnp.getConnection().isEquivalentTo(thatnp.getConnection())) {
                            found = true;
                            break;
                        }
                        else {
                            return false;
                        }
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        public String toString() {
            StringBuilder parentList = new StringBuilder("ID: ");
            parentList.append(id).append(", Parents");
            for (PathNode parent : parents) {
                parentList.append(": ").append(parent.getId());
            }

            // "ID: " + id + ", Parents" + parentList + ", Children: " + children;
            return parentList.append(", Children: ").append(children).toString();
        }
    }

    public static class NodePair implements Comparable{
        private String name;
        private PathNode connection;

        NodePair(String name, PathNode connection) {
            this.name = name;
            this.connection = connection;
        }

        public String getName() {
            return name;
        }

        public PathNode getConnection() {
            return connection;
        }

        void setConnection(PathNode connection) {
            this.connection = connection;
        }

        public String toString() {
            return "Name: " + name + ", Connection: " + connection.getId();
        }

        /* (non-Javadoc)
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        @Override
        public int compareTo(Object other) {
            return this.name.compareTo(((NodePair) other).name);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof NodePair)) {
                return false;
            }

            return this.name.equals(((NodePair) other).getName());
        }

        public int hashCode() {
            return name.hashCode();
        }
    }
}
