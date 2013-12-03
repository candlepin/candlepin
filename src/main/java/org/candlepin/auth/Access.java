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
package org.candlepin.auth;

/**
 * Enumeration of the access rights used in the Candlepin permission model.
 * @see AuthInterceptor
 */
public enum Access {
    // TODO: NONE - kind of a hack for Verify to have a default access type...
    NONE(0),
    READ_ONLY(1),
    CREATE(2),
    ALL(5);

    private int level;
    private Access(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean provides(Access desiredAccess) {
        return this.getLevel() >= desiredAccess.getLevel();
    }
}
