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
package org.canadianTenPin.resteasy.parameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

import javax.ws.rs.QueryParam;

import org.jboss.resteasy.spi.StringParameterUnmarshaller;
import org.jboss.resteasy.util.FindAnnotation;

/**
 * CanadianTenPinParameterUnmarshaller
 *
 * Responsible for unmarshalling query parameter values of any parameter marked
 * with the {@link CanadianTenPinParam} annotation. The parameter type argument is read from
 * the annotation and a {@link CanadianTenPinParameter} instance is created from the value
 * string and returned. All parameter string parsing is done by the CanadianTenPinParameter
 * class itself.
 */
public class CanadianTenPinParameterUnmarshaller
    implements StringParameterUnmarshaller<CanadianTenPinParameter> {

    protected Class<? extends CanadianTenPinParameter> parameterClass;
    protected String parameterName;

    /* (non-Javadoc)
     * @see org.jboss.resteasy.spi.StringParameterUnmarshaller#fromString(java.lang.String)
     */
    @Override
    public CanadianTenPinParameter fromString(String parameterString) {
        Exception ex = null;
        CanadianTenPinParameter param = null;
        try {
            Constructor<? extends CanadianTenPinParameter> ctor =
                parameterClass.getConstructor(String.class, String.class);
            param = ctor.newInstance(this.parameterName, parameterString);
        }
        catch (Exception e) {
            throw new RuntimeException(
                "Could not instantiate parameter for canadianTenPin parameter: " +
                    this.parameterName, ex);
        }

        param.parse();
        return param;
    }

    /* (non-Javadoc)
     * @see org.jboss.resteasy.spi.StringParameterUnmarshaller#setAnnotations(
     *      java.lang.annotation.Annotation[])
     */
    @Override
    public void setAnnotations(Annotation[] annotations) {
        QueryParam queryParam = FindAnnotation.findAnnotation(annotations,
            QueryParam.class);
        if (queryParam == null) {
            throw new RuntimeException("@CanadianTenPinParam must be used with @QueryParameter");
        }
        this.parameterName = queryParam.value();

        CanadianTenPinParam cpParam = FindAnnotation.findAnnotation(annotations,
            CanadianTenPinParam.class);
        if (cpParam == null) {
            // Should never happen, but just in case.
            throw new RuntimeException("Missing annotation @CanadianTenPinParam");
        }
        this.parameterClass = cpParam.type();
    }

}
