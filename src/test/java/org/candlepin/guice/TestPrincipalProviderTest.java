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
package org.candlepin.guice;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class TestPrincipalProviderTest {

    private final TestPrincipalProvider provider = new TestPrincipalProvider();

    @AfterEach
    public void cleanup() {
        TestPrincipalProvider.clearPrincipal();
    }

    @Test
    public void testGetReturnsDefaultPrincipalWhenNoneSet() {
        Principal principal = this.provider.get();

        assertThat(principal.getName())
            .isEqualTo("Default User");
    }

    @Test
    public void testSetPrincipalThenGetReturnsSamePrincipalOnSameThread() {
        Principal principal = new UserPrincipal("thread-main-owner", List.of(), false);

        TestPrincipalProvider.setPrincipal(principal);

        assertThat(this.provider.get())
            .isSameAs(principal);
    }

    @Test
    public void testClearPrincipalResetsToDefault() {
        Principal principal = new UserPrincipal("custom-owner", List.of(), false);
        TestPrincipalProvider.setPrincipal(principal);

        TestPrincipalProvider.clearPrincipal();

        assertThat(this.provider.get().getName())
            .isEqualTo("Default User");
    }

    @Test
    public void testPrincipalsAreIsolatedPerThread() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        AtomicReference<Principal> thread1Principal = new AtomicReference<>();
        AtomicReference<Principal> thread2Principal = new AtomicReference<>();

        Thread thread1 = new Thread(() -> {
            runIsolated(new UserPrincipal("thread-1-owner", List.of(), false), ready, go, thread1Principal);
        });

        Thread thread2 = new Thread(() -> {
            runIsolated(new UserPrincipal("thread-2-owner", List.of(), false), ready, go, thread2Principal);
        });

        thread1.start();
        thread2.start();
        ready.await();
        go.countDown();
        thread1.join();
        thread2.join();

        assertThat(thread1Principal.get().getName())
            .isEqualTo("thread-1-owner");
        assertThat(thread2Principal.get().getName())
            .isEqualTo("thread-2-owner");
    }

    private void runIsolated(Principal principal, CountDownLatch ready, CountDownLatch go,
        AtomicReference<Principal> result) {

        try {
            TestPrincipalProvider.setPrincipal(principal);
            ready.countDown();
            go.await();
            result.set(this.provider.get());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        finally {
            TestPrincipalProvider.clearPrincipal();
        }
    }

}
