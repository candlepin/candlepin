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
package org.candlepin.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



/**
 * Indicates to any application performance managers that this method should be recorded as part of a trace.
 * By default it will only include itself into an already-in-progress trace. However it can be made to start
 * a new trace if one isn't already in progress by specifying {@code @Traceable(startable = true)}.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface Traceable {

    /**
     * Indicates if the method should be allowed to start a new trace if a trace isn't already in progress.
     *
     * @return
     *  true if a new trace is allowed to start if needed, false otherwise
     */
    boolean startable() default false;
}
