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
package org.candlepin.common.util;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.common.exceptions.CandlepinParameterParseException;
import org.candlepin.common.exceptions.ExceptionMessage;
import org.candlepin.common.exceptions.mappers.TestExceptionMapperBase.MapperTestModule;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;


/**
 * Test of JaxRsExceptionResponseBuilder
 */
@ExtendWith(MockitoExtension.class)
public class JaxRsExceptionResponseBuilderTest {
    private JaxRsExceptionResponseBuilder exceptionBuilder;

    @BeforeEach
    public void injector() {
        MapperTestModule mtm = new MapperTestModule(JaxRsExceptionResponseBuilder.class);
        Injector injector = Guice.createInjector(mtm);
        exceptionBuilder = injector.getInstance(JaxRsExceptionResponseBuilder.class);
    }

    @Test
    public void jaxWsRsError() {
        String foo = "javax.ws.rs.SomeThing(\"paramName\") value is 'strVal' for";
        Response resp = exceptionBuilder.getResponse(new RuntimeException(foo));
        ExceptionMessage e =  (ExceptionMessage) resp.getEntity();

        assertTrue(exceptionBuilder.canHandle(new RuntimeException(foo)));
        assertTrue(e.getDisplayMessage().contains("paramName"));
        assertTrue(e.getDisplayMessage().contains("strVal"));
    }

    @Test
    public void failsFastIfNonHandeableGetResponseCalled() {
        Exception ex = new RuntimeException();
        assertFalse(exceptionBuilder.canHandle(ex));
        //the following should throw IllegalArgumentException

        assertThrows(IllegalArgumentException.class, () -> exceptionBuilder.getResponse(ex));
    }

    @Test
    public void candlepinParserError() {
        CandlepinParameterParseException parseEx =
            new CandlepinParameterParseException("thisFormat");
        RuntimeException ex = new RuntimeException(parseEx);
        Response resp = exceptionBuilder.getResponse(ex);
        ExceptionMessage e =  (ExceptionMessage) resp.getEntity();

        assertTrue(exceptionBuilder.canHandle(ex));
        assertTrue(e.getDisplayMessage().contains("thisFormat"));
    }

    @Test
    public void badExceptionNotHandleable() {
        String foo = "javax.Xs.rs.SomeThing(\"paramName\") value is 'strVal' for";
        assertFalse(exceptionBuilder.canHandle(new RuntimeException(foo)));
    }

    @Test
    public void badMessageNotHandleable() {
        String foo = "javax.ws.rs.SomeThing(\"paramName\") value is strVal for";
        assertFalse(exceptionBuilder.canHandle(new RuntimeException(foo)));
    }
}
