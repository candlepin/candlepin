/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.resteasy.converter;

import org.candlepin.common.exceptions.CandlepinParameterParseException;
import org.candlepin.dto.api.v1.KeyValueParamDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.ext.ParamConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KeyValueParamConverterProviderTest {

    private static final String KEY = "test_key";
    private static final String VALUE = "test_value";
    private static final String FULL_PAIR = KEY + ":" + VALUE;
    private static final String PAIR_MISSING_VALUE = KEY + ":";
    private static final String VALUE_WITH_COLON = "test_value:test";
    private static final String PAIR_WITH_COLON = KEY + ":" + VALUE_WITH_COLON;

    private ParamConverter<KeyValueParamDTO> keyValConverter;

    @BeforeEach
    void setUp() {
        this.keyValConverter = new KeyValueParamConverterProvider()
            .getConverter(KeyValueParamDTO.class, KeyValueParamDTO.class, null);
    }

    @Test
    public void canParseKeyValue() {
        KeyValueParamDTO keyVal = this.keyValConverter.fromString(FULL_PAIR);

        assertEquals(KEY, keyVal.getKey());
        assertEquals(VALUE, keyVal.getValue());
    }

    @Test
    public void valueIsOptional() {
        KeyValueParamDTO keyVal = this.keyValConverter.fromString(PAIR_MISSING_VALUE);

        assertEquals(KEY, keyVal.getKey());
        assertEquals("", keyVal.getValue());
    }

    @Test
    public void valueCanContainColon() {
        KeyValueParamDTO keyVal = this.keyValConverter.fromString(PAIR_WITH_COLON);

        assertEquals(KEY, keyVal.getKey());
        assertEquals(VALUE_WITH_COLON, keyVal.getValue());
    }

    @Test
    public void pairWithMissingSeparatorIsRejected() {
        assertThrows(CandlepinParameterParseException.class, () -> this.keyValConverter.fromString(KEY));
    }

}
