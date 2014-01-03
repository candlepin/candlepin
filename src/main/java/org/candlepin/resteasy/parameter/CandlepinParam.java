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
package org.candlepin.resteasy.parameter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.jboss.resteasy.annotations.StringParameterUnmarshallerBinder;

/**
 * CandlepinParam
 *
 * Marks a QueryParam to customize parameter value unmarshalling
 * by RestEasy. A parameter 'type' class must be specified and will be
 * the type of the returned parameter object.
 *
 * <pre>
 * For example:
 *
 * @QueryParam("my_param") @CandlepinParam(type=KeyValueParameter.class)
 * List<KeyValueParameter> attributeFileterParams
 * </pre>
 *
 * @see CandlepinParameter
 */
@Retention(RetentionPolicy.RUNTIME)
@StringParameterUnmarshallerBinder(CandlepinParameterUnmarshaller.class)
public @interface CandlepinParam {
    Class<? extends CandlepinParameter> type();
}
