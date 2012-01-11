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
package org.candlepin.util;

/**
 * This is for tito's release numbering scheme. Example: 0.4.5 will become 0.5.0, not 0.5.
 */
public class Version implements Comparable<Version> {

    private Integer major;
    private Integer minor;
    private Integer incremental;

    public Version(Integer major, Integer minor, Integer incremental) {
        this.major = major;
        this.minor = minor;
        this.incremental = incremental;

    }

    public Version(String version) {
        //split off the release: 0.0.0-0 becomes 0.0.0
        String[] noDash = version.split("\\-", 2);
        String[] tokens = noDash[0].split("\\.", 3);
        this.major = Integer.parseInt(tokens[0]);
        this.minor = Integer.parseInt(tokens[1]);
        this.incremental = Integer.parseInt(tokens[2]);
    }

    @Override
    public int compareTo(Version v) {
        int majorCmp = this.major.compareTo(v.major);
        if (majorCmp == 0) {
            int minorCmp = this.minor.compareTo(v.minor);
            if (minorCmp == 0) {
                int incCmp = this.incremental.compareTo(v.incremental);
                if (incCmp == 0) {
                    //versions are identical
                    return 0;

                }
                else {
                    return incCmp;
                }
            }
            else {
                return minorCmp;
            }
        }
        else {
            return majorCmp;
        }

    }

}
