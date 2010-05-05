/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.auth.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.fedoraproject.candlepin.auth.Role;

/**
 * Annotation specifying which roles can access a given method. 
 * Can be applied to both the class, or a method within the class, where the latter takes
 * precedence. 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD/*, ElementType.TYPE*/})
@Inherited
public @interface AllowRoles {

    Role [] roles() default {};
}
