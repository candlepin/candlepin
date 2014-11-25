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

package org.candlepin.policy.js.compliance.hash;


/**
 * An object that is responsible for generating a {@link String}
 * representation of T that will be included in a hash.
 *
 * @param <T> the object to generate a hashable {@link String}.
 */
public interface HashableStringGenerator<T> {

    /**
     * Generates a {@link String} that is to be included in a hash.
     * @param toConvert the object to convert.
     * @return a string representation of all hashable data for this object.
     */
    String generate(T toConvert);

}
