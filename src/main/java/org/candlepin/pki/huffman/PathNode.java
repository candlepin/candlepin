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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PathNode {
    private long id = 0;
    private List<NodePair> children = new ArrayList<>();
    private List<PathNode> parents = new ArrayList<>();

    public PathNode(long id) {
        this.id = id;
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

    /**
     * Determine if the nodes are equivalent. This node is equivalent to the other node if each
     * point is true:
     *
     * <ul>
     *   <li>Every child in this node has a matching child in the other node with the same name.
     *   <li>The matching child-pairs' connections are equivalent.
     * </ul>
     *
     * Alternatively, if the two nodes are actually the same node, then they are equivalent.
     *
     * @param that the comparing node
     * @return true if this node is equivalent to that node.
     */
    boolean isEquivalentTo(PathNode that) {
        if (this.getId() == that.getId()) {
            return true;
        }
        // same number of children with the same names for child nodes
        if (this.getChildren().size() != that.getChildren().size()) {
            return false;
        }
        for (NodePair thisNodePair : this.getChildren()) {
            boolean found = false;
            for (NodePair thatNodePair : that.getChildren()) {
                // Does "this" node have a child node that "that" node has?
                if (thisNodePair.getName().equals(thatNodePair.getName())) {
                    // Yes. Are the child nodes' connections equivalent?
                    if (thisNodePair.getConnection().isEquivalentTo(thatNodePair.getConnection())) {
                        // Yes. Look for the next child compare.
                        found = true;
                        break;
                    }
                    else {
                        // No, the child nodes' connections aren't equivalent.
                        // So, "this" and "that" are not equivalent.
                        return false;
                    }
                }
            }
            // If "this" has a child node not found in "that", then the nodes are not equivalent.
            if (!found) {
                return false;
            }
        }
        return true;
    }

    @Override
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
