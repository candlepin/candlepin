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
package org.candlepin.exceptions.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.exceptions.mappers.TestExceptionMapperBase.MapperTestModule;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.NotFoundException;
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
    public void badExceptionNotHandleable() {
        String foo = "javax.Xs.rs.SomeThing(\"paramName\") value is 'strVal' for";
        assertFalse(exceptionBuilder.canHandle(new RuntimeException(foo)));
    }

    @Test
    public void badMessageNotHandleable() {
        String foo = "javax.ws.rs.SomeThing(\"paramName\") value is strVal for";
        assertFalse(exceptionBuilder.canHandle(new RuntimeException(foo)));
    }

    @Test
    public void canHandleIncorrectDateTimeFormats() {
        NotFoundException ex = new NotFoundException("Unable to extract parameter from http request: " +
            "javax.ws.rs.QueryParam(\"param\") value is '2019-01-16T01:02:03-0400' for public",
            new RuntimeException("Invalid Date"));

        assertTrue(exceptionBuilder.canHandle(ex));

        Response resp = exceptionBuilder.getResponse(ex);
        ExceptionMessage e =  (ExceptionMessage) resp.getEntity();

        assertTrue(e.getDisplayMessage().contains("is not a valid value for"));
    }
}
