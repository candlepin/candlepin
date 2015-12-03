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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.common.config.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.ws.rs.core.MediaType;

@RunWith(MockitoJUnitRunner.class)
public class JsonProviderTest {

    @Mock private Configuration config;

    // This is kind of silly - basically just testing an initial setting...
    @Test
    public void dateFormat() {
        JsonProvider provider = new JsonProvider(config);

        boolean datesAsTimestamps = isEnabled(provider,
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        assertFalse(datesAsTimestamps);
    }

    // This tests to see that the ObjectMapper serializes Date objects to the proper format
    @Test
    public void serializedDateDoesNotIncludeMilliseconds() throws JsonProcessingException {
        Date now = new Date();  // will be initialized to when it was allocated with millisecond precision
        SimpleDateFormat iso8601WithoutMilliseconds = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        String expectedDate = "\"" + iso8601WithoutMilliseconds.format(now) + "\"";
        JsonProvider provider = new JsonProvider(config);
        ObjectMapper mapper = provider.locateMapper(Object.class,
                MediaType.APPLICATION_JSON_TYPE);
        String serializedDate = mapper.writeValueAsString(now);
        assertTrue(serializedDate.equals(expectedDate));
    }


    private boolean isEnabled(JsonProvider provider, SerializationFeature feature) {
        ObjectMapper mapper = provider.locateMapper(Object.class,
                MediaType.APPLICATION_JSON_TYPE);
        SerializationConfig sConfig = mapper.getSerializationConfig();
        return sConfig.isEnabled(feature);
    }

}
