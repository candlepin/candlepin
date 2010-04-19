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

import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.internal.Nullable;

/**
 * I18nProvider
 */
public class I18nProvider implements Provider<I18n> {
    private I18n i18n;

    @Inject
    public I18nProvider(@Nullable HttpServletRequest request) {
        Locale locale = null;
        
        if (request != null) {
            locale = request.getLocale();
        }
        
        i18n = I18nFactory.getI18n(
            getClass(), 
            locale == null ? Locale.US : locale,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK
        );
    }
    
    @Override
    public I18n get() {
        return i18n;
    }
}
