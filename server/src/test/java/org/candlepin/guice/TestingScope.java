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

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;

import java.util.HashMap;
import java.util.Map;

/**
 * Noop scope for testing.  Inspired by Jukito: https://github.com/ArcBees/Jukito
 */
public class TestingScope {
    private static class Singleton implements Scope {

        private final String simpleName;

        private final Map<Key<?>, Object> backingMap = new HashMap<>();

        public Singleton(String simpleName) {
            this.simpleName = simpleName;
        }

        public void clear() {
            backingMap.clear();
        }

        @Override
        public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscoped) {
            return new Provider<T>() {

                @SuppressWarnings("unchecked")
                public T get() {

                    Object o = backingMap.get(key);

                    if (o == null) {
                        o = unscoped.get();
                        backingMap.put(key, o);
                    }
                    return (T) o;
                }
            };
        }

        public String toString() {
            return simpleName;
        }
    }

    private TestingScope() {
        // private constructor
    }

    /**
     * Test-scoped singletons are typically used in test cases for objects that
     * correspond to singletons in the application. Your JUnit test case must use
     * {@link JukitoRunner} on its {@code @RunWith} annotation so that
     * test-scoped singletons are reset before every test case.
     * <p/>
     * If you want your singleton to be instantiated automatically with each new
     * test, use {@link #EAGER_SINGLETON}.
     */
    public static final Singleton SINGLETON = new Singleton("TestSingleton");

    /**
     * Eager test-scoped singleton are similar to test-scoped {@link #SINGLETON}
     * but they get instantiated automatically with each new test.
     */
    public static final Singleton EAGER_SINGLETON = new Singleton("EagerTestSingleton");

    /**
     * Clears all the instances of test-scoped singletons. After this method is
     * called, any "singleton" bound to this scope that had already been created
     * will be created again next time it gets injected.
     */
    public static void clear() {
        SINGLETON.clear();
        EAGER_SINGLETON.clear();
    }
}
