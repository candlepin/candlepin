/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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

package org.candlepin.controller.util;

/**
 * Defines a contract for requesting environment specific content prefixes.
 */
@FunctionalInterface
public interface ContentPrefix {

    /**
     * Returns a content prefix for the given environment id.
     *
     * @param environmentId id of an environment for which to return a content prefix
     * @return environment specific content prefix
     */
    String get(String environmentId);

}
