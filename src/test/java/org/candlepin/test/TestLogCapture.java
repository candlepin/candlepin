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
package org.candlepin.test;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures log events from the current test thread without changing logger configuration
 */
public class TestLogCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {
    private final Logger logger;
    private final String threadName;
    private final List<ILoggingEvent> events = new ArrayList<>();

    public TestLogCapture(Class<?> loggerClass) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerClass);
        this.threadName = Thread.currentThread().getName();
        this.start();
        this.logger.addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (this.threadName.equals(event.getThreadName()) && event.getLevel().toInt() >=
            ch.qos.logback.classic.Level.WARN_INT) {
            this.events.add(event);
        }
    }

    public List<ILoggingEvent> getEvents() {
        return List.copyOf(this.events);
    }

    @Override
    public void close() {
        this.logger.detachAppender(this);
        this.stop();
    }
}
