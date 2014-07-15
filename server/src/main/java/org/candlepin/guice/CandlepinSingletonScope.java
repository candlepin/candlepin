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

import java.util.HashMap;
import java.util.Map;

import org.jboss.resteasy.spi.ResteasyProviderFactory;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;

/**
 * CandlepinSingletonScope
 *
 * A per-request / per-unit of work guice singleton scope.
 */
public class CandlepinSingletonScope implements Scope {

    public void enter() {
        ResteasyProviderFactory.pushContext(CandlepinSingletonScopeData.class,
            new CandlepinSingletonScopeData());
    }

    public void exit() {
        ResteasyProviderFactory.popContextData(CandlepinSingletonScopeData.class);
    }

    public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscoped) {
        return new Provider<T>() {
            public T get() {
                Map<Key<?>, Object> scopedObjects = getScopedObjectMap(key);

                @SuppressWarnings("unchecked")
                T current = (T) scopedObjects.get(key);
                if (current == null && !scopedObjects.containsKey(key)) {
                    current = unscoped.get();
                    scopedObjects.put(key, current);
                }
                return current;
            }
        };
    }

    private <T> Map<Key<?>, Object> getScopedObjectMap(Key<T> key) {
        CandlepinSingletonScopeData scopeData = ResteasyProviderFactory.getContextData(
            CandlepinSingletonScopeData.class);
        if (scopeData == null) {
            throw new OutOfScopeException("Cannot access " + key +
                " outside of a scoping block");
        }
        return scopeData.get();
    }

    /**
     * CandlepinSingletonScopeData class to hold the local session scoped data map.
     *
     * We really need single per resteasy session, so let resteasy handle scoping for us.
     */
    private class CandlepinSingletonScopeData {
        private Map<Key<?>, Object> scopeData = new HashMap<Key<?>, Object>();

        public Map<Key<?>, Object> get() {
            return scopeData;
        }
    }
}
