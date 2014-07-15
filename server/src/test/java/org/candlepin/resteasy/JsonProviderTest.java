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
package org.candlepin.resteasy;

import static org.mockito.Mockito.when;

import org.candlepin.config.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.core.MediaType;

import junit.framework.Assert;

@RunWith(MockitoJUnitRunner.class)
public class JsonProviderTest {

    @Mock private Config config;

    @Test
    public void noIndentation() {
        when(config.indentJson()).thenReturn(false);

        JsonProvider provider = new JsonProvider(config);
        boolean indentEnabled = isEnabled(provider,
                SerializationFeature.INDENT_OUTPUT);

        Assert.assertFalse(indentEnabled);
    }

    @Test
    public void indentation() {
        when(config.indentJson()).thenReturn(true);

        JsonProvider provider = new JsonProvider(config);
        boolean indentEnabled = isEnabled(provider,
                SerializationFeature.INDENT_OUTPUT);

        Assert.assertTrue(indentEnabled);
    }

    // This is kind of silly - basically just testing an initial setting...
    @Test
    public void dateFormat() {
        JsonProvider provider = new JsonProvider(config);

        boolean datesAsTimestamps = isEnabled(provider,
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Assert.assertFalse(datesAsTimestamps);
    }


    private boolean isEnabled(JsonProvider provider, SerializationFeature feature) {
        ObjectMapper mapper = provider.locateMapper(Object.class,
                MediaType.APPLICATION_JSON_TYPE);
        SerializationConfig sConfig = mapper.getSerializationConfig();
        return sConfig.isEnabled(feature);
    }

}
