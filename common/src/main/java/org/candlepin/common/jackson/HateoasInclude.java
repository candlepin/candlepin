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
package org.candlepin.common.jackson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



/**
 * The HateoasInclude annotation is used in conjunction with the HateoasBeanPropertyFilter and
 * jackson's JsonFilter annotation to determine which properties are to be included in the
 * JSON-serialized output of a model object.
 *
 * The HateoasInclude annotation may be applied to a class, a property or a method and will be
 * checked in that order. When applied to the class, all properties within the class will be
 * included. Otherwise, only the properties with the annotation will be included.
 *
 * Additionally, if the annotation is applied to an accessor in the form of "get<property>" or
 * "is<property>", the property will be included as if it the annotation were applied directly to it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.LOCAL_VARIABLE, ElementType.METHOD})
public @interface HateoasInclude {
}
