/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.guice;

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.inject.Injector;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * I18nProviderTest
 */
public class I18nProviderTest {
    @Mock private HttpServletRequest request;
    @Mock private Injector injector;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(injector.getInstance(HttpServletRequest.class)).thenReturn(request);
    }

    @Test
    public void verifyEnglishTestString() {
        when(request.getLocale()).thenReturn(Locale.US);
        I18nProvider provider = new I18nProvider(injector);
        assertEquals("Bad Request", provider.getTestString());
    }

    @Test
    public void verifyGermanTestString() {
        when(request.getLocale()).thenReturn(new Locale("de", "DE"));
        I18nProvider provider = new I18nProvider(injector);
        assertEquals("Fehlerhafte Anfrage", provider.getTestString());
    }
}
