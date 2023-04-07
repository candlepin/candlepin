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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

public class I18nProviderTest {

    private HttpServletRequest request;

    @BeforeEach
    public void setUp() {
        this.request = mock(HttpServletRequest.class);
    }

    @Test
    public void shouldDefaultToUsLocale() {
        I18nProvider provider = new I18nProvider(() -> this.request);

        I18n i18n = provider.get();

        assertEquals(Locale.US, i18n.getLocale());
    }

    @Test
    public void shouldHandleConcurrentAccess() {
        when(this.request.getLocale()).thenReturn(Locale.FRANCE);
        I18nProvider provider = new I18nProvider(() -> this.request);
        CountDownLatch readyCounter = new CountDownLatch(5);
        CountDownLatch doneCounter = new CountDownLatch(5);

        List<Worker> workers = Stream
            .generate(() -> new Worker(readyCounter, doneCounter, provider))
            .limit(5)
            .collect(Collectors.toList());

        workers.forEach(worker -> new Thread(worker).start());

        awaitAll(doneCounter);

        for (Worker worker : workers) {
            assertEquals(Locale.FRANCE, worker.result().getLocale());
        }
    }

    private static void awaitAll(CountDownLatch counter) {
        try {
            counter.await();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Worker implements Runnable {

        private final CountDownLatch readyCounter;
        private final CountDownLatch doneCounter;
        private final I18nProvider provider;
        private I18n result;

        public Worker(CountDownLatch readyCounter, CountDownLatch doneCounter, I18nProvider provider) {
            this.readyCounter = readyCounter;
            this.doneCounter = doneCounter;
            this.provider = provider;
        }

        @Override
        public void run() {
            this.readyCounter.countDown();
            awaitAll(this.readyCounter);
            this.result = this.provider.get();
            this.doneCounter.countDown();
        }

        public I18n result() {
            return this.result;
        }
    }

}
