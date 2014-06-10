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
package org.candlepin.gutterball.guice;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.inject.Provider;

import org.jukito.JukitoRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnap.commons.i18n.I18n;

import java.util.Locale;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

@RunWith(JukitoRunner.class)
public class I18nProviderTest {

    @Inject HttpServletRequest mockReq;

    @Test
    public void testGetFrench() {
        when(mockReq.getLocale()).thenReturn(Locale.FRANCE);
        Provider<I18n> p = new I18nProvider(mockReq);
        I18n providedI18n = p.get();
        assertEquals(Locale.FRANCE, providedI18n.getLocale());
    }

    @Test
    public void testGetJapanese() {
        when(mockReq.getLocale()).thenReturn(Locale.JAPAN);
        Provider<I18n> p = new I18nProvider(mockReq);
        I18n providedI18n = p.get();
        assertEquals(Locale.JAPAN, providedI18n.getLocale());
    }

    @Test
    public void testGetDefault() {
        when(mockReq.getLocale()).thenReturn(null);
        Provider<I18n> p = new I18nProvider(mockReq);
        I18n providedI18n = p.get();
        assertEquals(Locale.US, providedI18n.getLocale());
    }
}
