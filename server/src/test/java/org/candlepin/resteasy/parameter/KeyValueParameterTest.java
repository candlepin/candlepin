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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * KeyValueParameterTest
 */
public class KeyValueParameterTest {

    @Test
    public void validParsing() {
        KeyValueParameter param = new KeyValueParameter("param:paramValue");
        assertEquals("param", param.getKey());
        assertEquals("paramValue", param.getValue());
    }

    @Test
    public void valueCanBeEmpty() {
        KeyValueParameter param = new KeyValueParameter("param:");
        assertEquals("param", param.getKey());
        assertEquals("", param.getValue());
    }

    @Test
    public void parameterValueCanContainMultipleColins() {
        KeyValueParameter param = new KeyValueParameter("param:paramValue:c");
        assertEquals("param", param.getKey());
        assertEquals("paramValue:c", param.getValue());
    }

    @Test(expected = CandlepinParameterParseException.class)
    public void parameterValueCanNotBeEmpty() {
        KeyValueParameter param = new KeyValueParameter("");
    }

    @Test(expected = CandlepinParameterParseException.class)
    public void parameterValueCanNotBePropertyOnly() {
        KeyValueParameter param = new KeyValueParameter("param");
    }

    @Test(expected = CandlepinParameterParseException.class)
    public void throwsExceptionOnInvalidKeyValueFormat() {
        KeyValueParameter param = new KeyValueParameter("param|paramValue");
    }
}
