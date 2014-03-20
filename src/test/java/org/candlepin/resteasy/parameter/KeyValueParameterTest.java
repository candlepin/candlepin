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

import static org.junit.Assert.assertEquals;

import org.candlepin.exceptions.CandlepinParamterParseException;
import org.junit.Test;

/**
 * KeyValueParameterTest
 */
public class KeyValueParameterTest {

    @Test
    public void validParsing() {
        KeyValueParameter param = new KeyValueParameter("testparam", "param:paramValue");
        param.parse();
        assertEquals("param", param.key());
        assertEquals("paramValue", param.value());
    }

    @Test
    public void valueCanBeEmpty() {
        KeyValueParameter param = new KeyValueParameter("testparam", "param:");
        param.parse();
        assertEquals("param", param.key());
        assertEquals("", param.value());
    }

    @Test
    public void parameterValueCanContainMultipleColins() {
        KeyValueParameter param = new KeyValueParameter("testparam", "param:paramValue:c");
        param.parse();
        assertEquals("param", param.key());
        assertEquals("paramValue:c", param.value());
    }

    @Test(expected = CandlepinParamterParseException.class)
    public void parameterValueCanNotBeEmpty() {
        KeyValueParameter param = new KeyValueParameter("testparam", "");
        param.parse();
    }

    @Test(expected = CandlepinParamterParseException.class)
    public void parameterValueCanNotBePropertyOnly() {
        KeyValueParameter param = new KeyValueParameter("testparam", "param");
        param.parse();
    }

    @Test(expected = CandlepinParamterParseException.class)
    public void throwsExceptionOnInvalidKeyValueFormat() {
        KeyValueParameter param = new KeyValueParameter("testparam", "param|paramValue");
        param.parse();
    }

}
