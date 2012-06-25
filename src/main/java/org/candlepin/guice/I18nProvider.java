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
package org.candlepin.guice;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

/**
 * I18nProvider
 */
public class I18nProvider implements Provider<I18n> {
    private I18n i18n;

    public static final String BASENAME = "org.candlepin.i18n.Messages";

    private static Logger log = Logger.getLogger(I18nProvider.class);

    private static ConcurrentHashMap<Locale, I18n>
    cache = new ConcurrentHashMap<Locale, I18n>();

    @Inject
    public I18nProvider(Injector injector) {
        HttpServletRequest request = null;
        Locale locale = null;

        try {
            request = injector.getInstance(HttpServletRequest.class);
        }
        catch (ProvisionException e) {
            // This can happen in pinsetter, or anything else not in an http
            // request.
            // just ignore it.
        }

        if (request != null) {
            locale = request.getLocale();
        }

        locale = (locale == null) ? Locale.US : locale;

        if (log.isDebugEnabled()) {
            log.debug("Getting i18n engine for locale " + locale);
        }

        // If the locale does not exist, xnap is pretty inefficient.
        // This cache will hold the records more efficiently.
        // The logic below may leak a couple of items, but it
        // should clear up.
        i18n = cache.get(locale);
        if (i18n == null) {
            i18n = I18nFactory.getI18n(getClass(), BASENAME, locale,
                I18nFactory.FALLBACK);

            cache.put(locale, i18n);
        }
    }

    @Override
    public I18n get() {
        return i18n;
    }

    public String getTestString() {
        return i18n.tr("Bad Request");
    }
}
