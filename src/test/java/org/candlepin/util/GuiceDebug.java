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
package org.candlepin.util;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * GuiceDebug a class to enable debug logging in guice.
 * Usage: add GuiceDebug.enable(); to your code to see
 * the timings of things.
 */
public class GuiceDebug {
    private static Level oldLevel = Level.FINE;

    private GuiceDebug() {
        // default ctor
    }

    private static final Handler HANDLER = new ConsoleHandler() {
        {
            setLevel(Level.ALL); setFormatter(new Formatter() {
                public String format(LogRecord r) {
                    return String.format("[Debug Guice] %d %s%n",
                        r.getThreadID(), r.getMessage());
                }
            });
        }
    };

    public static void enable() {
        Logger guiceLogger = Logger.getLogger("com.google.inject");
        guiceLogger.addHandler(GuiceDebug.HANDLER);
        guiceLogger.setLevel(Level.ALL);
    }

    public static void disable() {
        Logger guiceLogger = Logger.getLogger("com.google.inject");
        guiceLogger.removeHandler(GuiceDebug.HANDLER);
        guiceLogger.setLevel(oldLevel);
    }
}
