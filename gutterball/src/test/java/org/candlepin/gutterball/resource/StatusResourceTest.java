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
package org.candlepin.gutterball.resource;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.gutterball.config.Configuration;
import org.candlepin.gutterball.config.MapConfiguration;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnap.commons.i18n.I18n;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

/**
 * StatusResourceTest
 */
@RunWith(JukitoRunner.class)
public class StatusResourceTest {
    @Inject StatusResource statusResource;

    @Test
    @SuppressWarnings("serial")
    public void testGetStatus(I18n i18n) {
        when(i18n.getLocale()).thenReturn(Locale.US);
        Map<String, String> expectedMap = new HashMap<String, String>() {{
            put("gutterball.version", "X.Y.Z");
            put("request_locale", Locale.US.toString());
        }};
        assertEquals(expectedMap, statusResource.getStatus());
    }

    @Test
    @SuppressWarnings("serial")
    public void testGetFrenchStatus(I18n i18n) {
        when(i18n.getLocale()).thenReturn(Locale.FRANCE);
        Map<String, String> expectedMap = new HashMap<String, String>() {{
            put("gutterball.version", "X.Y.Z");
            put("request_locale", Locale.FRANCE.toString());
        }};
        assertEquals(expectedMap, statusResource.getStatus());
    }

    public static class StatusConfig extends JukitoModule {
        @Override
        protected void configureTest() {
            Configuration c = new MapConfiguration();
            c.setProperty("gutterball.version", "X.Y.Z");
            bind(Configuration.class).toInstance(c);
        }
    }
}
