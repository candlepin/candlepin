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
package org.candlepin.pki;

/**
 * X509ExtensionWrapper
 */
public class X509ExtensionWrapper {
    private String oid = null;
    private boolean critical;
    private String value;

    public X509ExtensionWrapper(String oid, boolean critical,
        String value) {
        this.oid = oid;
        this.critical = critical;
        this.value = value;
    }

    public String toString() {
        return "[" + oid + " = " + value + "]";
    }

    public String getOid() {
        return oid;
    }

    public boolean isCritical() {
        return critical;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return oid.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        X509ExtensionWrapper other = (X509ExtensionWrapper) obj;
        if (oid == null) {
            if (other.oid != null) {
                return false;
            }
        }
        else if (!oid.equals(other.oid)) {
            return false;
        }
        return true;
    }

}
