/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.junit;

import org.candlepin.TestingModules;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

/**
 * JUnit 5 extension that maintains a single shared Guice injector for non-database unit tests
 * that need the standard four testing modules ({@link TestingModules.MockJpaModule},
 * {@link TestingModules.StandardTest}, {@link TestingModules.PKIModule},
 * {@link TestingModules.ServletEnvironmentModule}).
 *
 * <p>The injector is created lazily on first use and reused across all test classes that use
 * this extension. Test instances receive field and constructor injection via
 * {@link Injector#injectMembers(Object)}. Tests that need direct access to the injector
 * (e.g. for {@code getInstance()} calls) can use {@link #getInjector()}.
 */
public class GuiceExtension implements TestInstancePostProcessor {

    private static volatile Injector sharedInjector;
    private static final Object INJECTOR_LOCK = new Object();

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        ensureInjector().injectMembers(testInstance);
    }

    /**
     * Returns the shared Guice injector, creating it on first access.
     *
     * @return
     *  the shared injector
     */
    public static Injector getInjector() {
        return ensureInjector();
    }

    private static Injector ensureInjector() {
        Injector local = sharedInjector;
        if (local == null) {
            synchronized (INJECTOR_LOCK) {
                local = sharedInjector;
                if (local == null) {
                    local = Guice.createInjector(
                        new TestingModules.MockJpaModule(),
                        new TestingModules.StandardTest(),
                        new TestingModules.PKIModule(),
                        new TestingModules.ServletEnvironmentModule());
                    sharedInjector = local;
                }
            }
        }
        return local;
    }
}
