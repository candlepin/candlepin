/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

@Singleton
public class I18nProvider implements Provider<I18n> {
    private static final Logger log = LoggerFactory.getLogger(I18nProvider.class);
    public static final String BASENAME = "org.candlepin.common.i18n.Messages";
    private static final Map<Locale, I18n> CACHE = new ConcurrentHashMap<>();

    // TODO Use context to decouple it from request
    private final Provider<HttpServletRequest> request;

    @Inject
    public I18nProvider(Provider<HttpServletRequest> request) {
        this.request = request;
    }

    @Override
    public I18n get() {
        Locale locale = getLocale();

        return CACHE.computeIfAbsent(locale, requestedLocale -> {
            log.debug("Getting i18n engine for locale {}", requestedLocale);
            return I18nFactory.getI18n(getClass(), BASENAME, requestedLocale, I18nFactory.FALLBACK);
        });
    }

    private Locale getLocale() {
        Locale locale = null;

        try {
            locale = request.get().getLocale();
        }
        catch (ProvisionException e) {
            // This can happen in pinsetter, or anything else not in an http
            // request.
            // just ignore it.
        }

        locale = (locale == null) ? Locale.US : locale;
        return locale;
    }

}
