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

public class HuffNode {
    private long id = 0;
    private Object value = null;
    private int weight = 0;
    private HuffNode left = null;
    private HuffNode right = null;

    public HuffNode(long id, Object value, int weight, HuffNode left, HuffNode right) {
        this.value = value;
        this.weight = weight;
        this.left = left;
        this.right = right;
        this.id = id;
    }

    public HuffNode(long id, Object value, int weight) {
        this.value = value;
        this.weight = weight;
        this.id = id;
    }

    public static HuffNode merge(long id, HuffNode left, HuffNode right) {
        return new HuffNode(id, null, left.weight + right.weight, left, right);
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

    public long getId() {
        return this.id;
    }

    @Override
    public String toString() {
        return "ID: " + id +
            ", Value: " + value +
            ", Weight: " + weight +
            ", Left: " + left +
            ", Right: " + right;
    }
}
