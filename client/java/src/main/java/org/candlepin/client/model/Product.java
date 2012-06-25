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
package org.candlepin.client.model;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

/**
 * Product
 */
public class Product {

    private Extensions ext;
    private String hash;

    /**
     * @param ext
     * @param hash
     */
    public Product(Extensions ext, String hash) {
        this.ext = ext;
        this.hash = hash;
    }

    public Product() {
        super();
    }

    public int getHash() {
        return NumberUtils.toInt(hash, -1);
    }

    public String getName() {
        // hack. remove junk characters at the beginning of the name
        return ext.getValue("1").substring(4);
    }

    public String getVariant() {
        return ext.getValue("2");
    }

    public String getArchitecture() {
        return ext.getValue("3");
    }

    public String getVersion() {
        return ext.getValue("4");
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Product) {
            Product that = (Product) obj;
            return StringUtils.equals(this.hash, that.hash);
        }
        return false;
    }

}
