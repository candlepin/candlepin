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

import org.candlepin.common.guice.CommonI18nProvider;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Singleton;
/**
 * I18nProvider
 */
@Singleton
//@Component
public class I18nProvider extends CommonI18nProvider implements Provider<I18n> {
    private static Logger log = LoggerFactory.getLogger(I18nProvider.class);
    private static Map<Locale, I18n> cache = new ConcurrentHashMap<>();
    //private final Provider<HttpServletRequest> request;
    //private final HttpServletRequest request;

    @Inject
//    //@Autowired
//    public I18nProvider(Provider<HttpServletRequest> request) {
//        this.request = request;
//    }
    public I18nProvider() {

    }

    @Override
    public I18n get() {
        Locale locale = null;

        try {
            //locale = request.get().getLocale();
            //locale = new Locale.Builder().setLanguage("fr").setRegion("CA").build();
        }
        catch (ProvisionException e) {
            // This can happen in pinsetter, or anything else not in an http
            // request.
            // just ignore it.
        }

        locale = (locale == null) ? Locale.US : locale;

        // If the locale does not exist, xnap is pretty inefficient.
        // This cache will hold the records more efficiently.

        // see https://en.wikipedia.org/wiki/Double-checked_locking
        I18n i18n = cache.get(locale);
        if (i18n == null) {
            synchronized (cache) {
                i18n = cache.get(locale);
                if (i18n == null) {
                    log.debug("Getting i18n engine for locale {}", locale);

                    i18n = I18nFactory.getI18n(getClass(), getBaseName(), locale, I18nFactory.FALLBACK);
                    cache.put(locale, i18n);
                }
            }
        }
        return i18n;
    }

    public String getTestString() {
        return get().tr("Bad Request");
    }
}
