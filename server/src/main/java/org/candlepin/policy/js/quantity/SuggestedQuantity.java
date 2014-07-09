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
package org.candlepin.policy.js.quantity;

/**
 * SuggestedQuantity: Just used to transfer a couple values back from the quantity rules
 * namespace in just one invocation.
 */
public class SuggestedQuantity {

    private Long suggested;
    private Long increment;

    public Long getSuggested() {
        return suggested;
    }

    public void setSuggested(Long suggested) {
        this.suggested = suggested;
    }

    public Long getIncrement() {
        return increment;
    }

    public void setIncrement(Long increment) {
        this.increment = increment;
    }

}
