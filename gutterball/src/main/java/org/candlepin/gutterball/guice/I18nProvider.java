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

import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.servlet.ServletRequest;

/**
 * I18nProvider is a Guice Provider that returns an I18n instance matched
 * to the locale of the ServletRequest.
 * <p>
 * <b>Note that this Provider is RequestScoped!</b>  That means that a new Provider
 * will be created for every request.  If need a RequestScoped object in a broader
 * scope (like in a Singleton) inject a Provider for that class instead and call
 * get() whenever you need the object.  Our Resource classes are created per request
 * so we can inject the I18n object directly.
 * <p>
 * See http://code.google.com/p/google-guice/wiki/ServletModule#Using_RequestScope
 * for more information.
 */
@RequestScoped
public class I18nProvider implements Provider<I18n> {
    public static final String BASENAME = "org.candlepin.gutterball.i18n.Messages";

    private static Logger log = LoggerFactory.getLogger(I18nProvider.class);

    private static ConcurrentHashMap<Locale, I18n> cache = new ConcurrentHashMap<Locale, I18n>();

    private ServletRequest request;

    @Inject
    public I18nProvider(ServletRequest request) {
        this.request = request;
    }

    @Override
    public I18n get() {
        Locale locale = (request.getLocale() == null) ? Locale.US : request.getLocale();
        I18n i18n;

        // If the locale does not exist, xnap is pretty inefficient.
        // This cache will hold the records more efficiently.
        //
        // Make sure to keep the access wrapped in synchronized so we can
        // share across threads!
        synchronized (cache) {
            i18n = cache.get(locale);
            if (i18n == null) {
                i18n = I18nFactory.getI18n(getClass(), BASENAME, locale, I18nFactory.FALLBACK);
                cache.put(locale, i18n);
                log.debug("Getting i18n engine for locale {}", locale);
            }
        }
        return i18n;
    }

}
