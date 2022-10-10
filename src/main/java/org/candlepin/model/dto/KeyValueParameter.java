/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.model.dto;

import org.candlepin.exceptions.CandlepinParameterParseException;

import org.xnap.commons.i18n.I18n;

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
    public KeyValueParameter(I18n i18n, String inValue) {
        if (inValue == null) {
            throw new CandlepinParameterParseException(i18n, "key:value", "null");
        }
        String[] parts = inValue.split(":", 2); // Maximum of two parts
        if (parts.length == 1) {
            throw new CandlepinParameterParseException(i18n, "key:value", inValue);
        }

        key = parts[0];
        value = parts[1];
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

}
