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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.candlepin.gutterball.guice.I18nProvider;
import org.candlepin.gutterball.model.Status;

import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestScope;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnap.commons.i18n.I18n;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

import javax.inject.Inject;

/**
 * StatusResourceTest
 */
@RunWith(JukitoRunner.class)
public class StatusResourceTest {

    @Inject Provider<I18n> i18nProvider;

    @Test
    public void testGetStatus(I18n i18n) throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass().getClassLoader()
            .getResource("version.properties").toURI()));
        ps.println("version=TEST_V");
        ps.println("release=TEST_R");
        StatusResource statusResource = new StatusResource(i18nProvider);
        ps.close();

        when(i18n.getLocale()).thenReturn(Locale.US);
        Status status = statusResource.getStatus();
        assertEquals("TEST_V", status.getVersion());
        assertEquals("TEST_R", status.getRelease());
        assertEquals(Locale.US.toString(), statusResource.getStatus().getRequestLocale());
    }

    @Test
    public void testVersionAndReleaseDefaultsToUnknown(I18n i18n) throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass().getClassLoader()
            .getResource("version.properties").toURI()));
        ps.println("pfffft");
        StatusResource statusResource = new StatusResource(i18nProvider);
        ps.close();

        when(i18n.getLocale()).thenReturn(Locale.US);
        Status status = statusResource.getStatus();
        assertEquals("Unknown", status.getVersion());
        assertEquals("Unknown", status.getRelease());
    }

    @Ignore
    @Test
    @SuppressWarnings("serial")
    public void testGetFrenchStatus(I18n i18n) {
        StatusResource statusResource = new StatusResource(i18nProvider);
        when(i18n.getLocale()).thenReturn(Locale.FRANCE);
        assertEquals("X.Y.Z", statusResource.getStatus().getVersion());
        assertEquals(Locale.FRANCE.toString(), statusResource.getStatus().getRequestLocale());
    }

    public static class StatusConfig extends JukitoModule {
        @Override
        protected void configureTest() {
            bindScope(RequestScoped.class, TestScope.EAGER_SINGLETON);
            bind(I18n.class).toProvider(I18nProvider.class);
        }
    }
}
