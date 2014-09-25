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

package org.candlepin.gutterball.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.util.Map;

/**
 * A Jackson Converter that converts CP Owner JSON into just its 'key'. This
 * is needed in cases where we would like to map only the key instead of a
 * full owner object.
 *
 */
public class OwnerJsonToKeyConverter extends StdConverter<Map<String, Object>, String> {

    @Override
    public String convert(Map<String, Object> ownerData) {
        return ownerData != null && ownerData.containsKey("key") ? (String) ownerData.get("key") : "Unknown";
    }

}
