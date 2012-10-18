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
package org.candlepin.version;

/**
 * Represents a version of an Object marking the major, minor, and revision
 * in the format of major.minor.revision. i.e 1.2.3
 */
public class Version implements Comparable<Version> {
    private int major = 0;
    private int minor = 0;
    private int revision = 0;

    public Version(String version) {
        version = version == null || version.isEmpty() ? "" : version;
        String[] parts = version.split("\\.");
        if (parts.length > 0) {
            this.major = parseInt(parts[0]);
        }

        if (parts.length > 1) {
            this.minor = parseInt(parts[1]);
        }

        if (parts.length > 2) {
            this.revision = parseInt(parts[2]);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(Version toCompare) {

        if (toCompare == null) {
            return 1;
        }

        // Equal
        if (this.equals(toCompare)) {
            return 0;
        }

        if (this.major < toCompare.major) {
            return -1;
        }

        if (this.minor < toCompare.minor) {
            return -1;
        }

        if (this.revision < toCompare.revision) {
            return -1;
        }

        return 1;

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + major;
        result = prime * result + minor;
        result = prime * result + revision;
        return result;
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

        Version other = (Version) obj;
        if (major != other.major) {
            return false;
        }

        if (minor != other.minor) {
            return false;
        }

        if (revision != other.revision) {
            return false;
        }

        return true;
    }

    /*
     * Returns the String representation of this version.
     */
    @Override
    public String toString() {
        return major + "." + minor + "." + revision;
    }

    private int parseInt(String val) {
        if (val == null || val.isEmpty()) {
            return 0;
        }

        try {
            return new Integer(val).intValue();
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

}
