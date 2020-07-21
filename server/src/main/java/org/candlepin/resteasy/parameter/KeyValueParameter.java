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
package org.candlepin.resteasy.parameter;

import org.candlepin.common.exceptions.CandlepinParameterParseException;
import org.springframework.stereotype.Component;

/**
 * KeyValueParameter
 */
public class KeyValueParameter  {

    private String key;
    private String value;

    /**
     * A key/value query parameter implementation.
     *
     * <pre>
     *     FORMAT: paramName=key:value
     * </pre>
     *
     * @param inValue the value of the query parameter as entered in the URL
     *                            in the format: key:value
     */
    public KeyValueParameter(String inValue) {
        String[] parts = inValue.split(":", 2); // Maximum of two parts
        if (parts.length == 1) {
            throw new CandlepinParameterParseException("name:value");
        }

        key = parts[0];
        value = parts.length > 1 ? parts[1] : "";
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

}
