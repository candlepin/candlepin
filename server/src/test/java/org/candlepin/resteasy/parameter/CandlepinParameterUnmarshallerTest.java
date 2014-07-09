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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.annotation.Annotation;

import javax.ws.rs.QueryParam;

import org.junit.Before;
import org.junit.Test;

/**
 * CandlepinParameterUnmarshallerTest
 */
public class CandlepinParameterUnmarshallerTest {

    private CandlepinParameterUnmarshaller unmarshaller;

    @Before
    public void setUp() {
        unmarshaller = new CandlepinParameterUnmarshaller();
    }

    @Test
    public void requiresQueryParamAnnotation() throws Exception {
        Annotation[] annotations =
            AnnotationHolder.class.getMethod("missingQueryParamAnnotation")
                .getAnnotations();
        try {
            unmarshaller.setAnnotations(annotations);
            fail("Should have thrown RuntimeException.");
        }
        catch (RuntimeException e) {
            assertEquals("@CandlepinParam must be used with @QueryParameter",
                e.getMessage());
        }
    }

    @Test
    public void requiresCandlepinParamAnnotation() throws Exception {
        Annotation[] annotations =
            AnnotationHolder.class.getMethod("missingCandlepinParamAnnotation")
                .getAnnotations();
        try {
            unmarshaller.setAnnotations(annotations);
            fail("Should have thrown RuntimeException.");
        }
        catch (RuntimeException e) {
            assertEquals("Missing annotation @CandlepinParam", e.getMessage());
        }
    }

    @Test
    public void createAndInitializesParameterInstance() throws Exception {
        Annotation[] annotations =
            AnnotationHolder.class.getMethod("validAnnotations").getAnnotations();
        unmarshaller.setAnnotations(annotations);
        KeyValueParameter param = (KeyValueParameter) unmarshaller.fromString("cores:4");
        assertEquals("cores", param.key());
        assertEquals("4", param.value());
    }

    /**
     * Test class to allow access to the annotation sets that are required for
     * testing.
     */
    private class AnnotationHolder {
        @QueryParam("attributes") @CandlepinParam(type = KeyValueParameter.class)
        public void validAnnotations() { }

        @CandlepinParam(type = KeyValueParameter.class)
        public void missingQueryParamAnnotation() { }

        @QueryParam("missing-cp-param")
        public void missingCandlepinParamAnnotation() { }

    }

}
