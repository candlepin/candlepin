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

import org.candlepin.exceptions.CandlepinParamterParseException;

/**
 * CandlepinParameter
 *
 * Base class for any custom candlepin resteasy query parameters.
 *
 * Each parameter subclass must define how the parameter value should
 * be parsed.
 */
public abstract class CandlepinParameter {

    protected String paramName;
    protected String paramValue;

    public CandlepinParameter(String queryParamName, String queryParameterValue) {
        this.paramName = queryParamName;
        this.paramValue = queryParameterValue;
    }

    /**
     * Parses the parameter value into the usable bits required by this parameter.
     * This method is run by the {@link CandlepinParameterUnmarshaller} after it creates
     * an instance of this class.
     *
     * @throws CandlepinParamterParseException when the param value can not be parsed
     */
    abstract void parse() throws CandlepinParamterParseException;

}
