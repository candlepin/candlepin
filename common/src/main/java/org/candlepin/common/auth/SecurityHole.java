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
package org.candlepin.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to control access into a method. If noAuth is set to true, then the
 * system will attempt to authenticate the user, but will still let the call through
 * if authentication can not be done. If anon is set to true, then no
 * authentication will be done.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface SecurityHole {
    /**
     * Set to true if the method does not require an identity (e.g.
     * a ConsumerPrincipal) but the engine should try and create it first.
     */
    boolean noAuth() default false;

    /**
     * Set to true if the method does not require an identity (e.g.
     * a ConsumerPrincipal) and the engine should not try and create it.
     */
    boolean anon() default false;
}
