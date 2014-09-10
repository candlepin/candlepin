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

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.ProvisionException;
import com.google.inject.name.Names;
import javax.inject.Inject;
import javax.inject.Provider;
import org.candlepin.audit.EventSink;
import org.candlepin.common.exceptions.IseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider which returns the correct event sink for the current context.
 *
 * Return either the request or pinsetter scoped event sink, which allows us to queue
 * events and send them only if the request or job is successful.
 */
public class EventSinkProvider implements Provider<EventSink> {

    private static Logger log = LoggerFactory.getLogger(EventSinkProvider.class);

    private EventSink requestSink;
    private EventSink pinsetterSink;

    @Inject
    public EventSinkProvider(Injector injector) {
        // Guice doesn't surface any way to tell what scope you're in.  So we just have to
        // ask for the object and deal with any consequences if it isn't available.
        try {
            requestSink = injector.getInstance(Key.get(EventSink.class,
                    Names.named("RequestSink")));
        }
        catch (ProvisionException e) {
            requestSink = null;
        }

        try {
            pinsetterSink = injector.getInstance(Key.get(EventSink.class,
                    Names.named("PinsetterSink")));
        }
        catch (ProvisionException e) {
            pinsetterSink = null;
        }
    }

    @Override
    public EventSink get() {

        // These should not both be null, if so something is very wrong:
        if (requestSink != null && pinsetterSink != null) {
            log.error("Request sink and pinsetter sink are both non-null.");
            throw new IseException("Scope configuration error.");
        }

        if (requestSink != null) {
            return requestSink;
        }
        else if (pinsetterSink != null) {
            return pinsetterSink;
        }
        else {
            throw new OutOfScopeException("Not in PinsetterScope or ServletScope!");
        }
    }
}
