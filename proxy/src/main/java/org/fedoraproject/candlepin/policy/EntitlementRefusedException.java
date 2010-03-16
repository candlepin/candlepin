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
package org.fedoraproject.candlepin.policy;

/**
 * Thrown when an entitlement is not granted due to a rule failing.
 *
 * Carries information about all failures and warnings, which may be
 * passed on to callers.
 */
public class EntitlementRefusedException extends Exception {

    private static final long serialVersionUID = 1L;
    private ValidationResult result;

    public EntitlementRefusedException(ValidationResult result) {
        super("Entitlement refused");
        this.result = result;
    }

    public ValidationResult getResult() {
        return this.result;
    }
}
