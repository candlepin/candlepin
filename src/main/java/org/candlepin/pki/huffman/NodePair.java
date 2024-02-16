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

public class NodePair implements Comparable<NodePair> {
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

    @Override
    public int compareTo(NodePair other) {
        return this.name.compareTo(other.name);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof NodePair)) {
            return false;
        }

        return this.name.equals(((NodePair) other).getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "Name: " + name + ", Connection: " + connection.getId();
    }
}
