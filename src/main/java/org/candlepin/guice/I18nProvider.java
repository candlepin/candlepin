/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */
package org.candlepin.guice;

import org.candlepin.resteasy.filter.LocaleContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

@RequestScoped
public class I18nProvider {
    private static final Logger log = LoggerFactory.getLogger(I18nProvider.class);
    private static final Map<Locale, I18n> cache = new ConcurrentHashMap<>();
    public static final String BASENAME = "org.candlepin.common.i18n.Messages";

    private final LocaleContext localeContext;

    @Inject
    public I18nProvider(LocaleContext localeContext) {
        this.localeContext = localeContext;
    }

    @Produces
    @RequestScoped
    public I18n get() {
        Locale locale = this.localeContext.currentLocale();

        return cache.computeIfAbsent(locale, requestedLocale -> {
            log.debug("Getting i18n engine for locale {}", requestedLocale);
            return I18nFactory.getI18n(getClass(), BASENAME, requestedLocale, I18nFactory.FALLBACK);
        });
    }

    public String getTestString() {
        return get().tr("Bad Request");
    }
}
