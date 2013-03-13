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
package org.candlepin.policy.js;

/**
 * Thrown if there is a problem parsing the Rules definition file.
 */
public class RuleParseException extends RuntimeException {

    private static final long serialVersionUID = -1089224966478743419L;

    public RuleParseException(Throwable t) {
        super(t);
    }

    public RuleParseException(String msg) {
        super(msg);
    }

}
