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

import org.candlepin.common.guice.CommonI18nProvider;

import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

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
public class I18nProvider extends CommonI18nProvider implements Provider<I18n> {

    private static Logger log = LoggerFactory.getLogger(I18nProvider.class);

    @Inject
    public I18nProvider(ServletRequest request) {
        super(request);
    }
}
