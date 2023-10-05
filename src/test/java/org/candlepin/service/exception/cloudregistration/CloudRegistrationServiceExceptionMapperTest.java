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
package org.candlepin.service.exception.cloudregistration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.TestingModules;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.NotAuthorizedException;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;



public class CloudRegistrationServiceExceptionMapperTest {

    I18n i18n;

    @BeforeEach
    public void init() {
        Injector injector = Guice.createInjector(
            new TestingModules.ServletEnvironmentModule());
        i18n = injector.getInstance(I18n.class);
    }

    @Test
    public void exceptionMappingTest() {
        String message = "oops";
        String type = "test_type";
        assertEquals(i18n.tr("Cloud provider or account details could not be resolved to an organization"),
            assertThrows(NotAuthorizedException.class,
                () -> CloudRegistrationServiceExceptionMapper.map(
                    new CloudRegistrationAuthorizationException(),
                    type, i18n))
                .getMessage());
        assertEquals(i18n.tr("Request is missing cloud provider type (e.g. amazon, gcp, azure)"),
            assertThrows(BadRequestException.class,
                () -> CloudRegistrationServiceExceptionMapper.map(new CloudRegistrationMissingTypeException(),
                    type, i18n))
                .getMessage());
        assertEquals(i18n.tr("Unrecognized provider type in request: {0}", type),
            assertThrows(IllegalArgumentException.class,
                () -> CloudRegistrationServiceExceptionMapper.map(new CloudRegistrationBadTypeException(),
                    type, i18n))
                .getMessage());
        assertEquals(i18n.tr("Unable to retrieve organization ID with provided meta-data"),
            assertThrows(BadRequestException.class,
                () -> CloudRegistrationServiceExceptionMapper.map(new CloudRegistrationBadMetadataException(),
                    type, i18n))
                .getMessage());
        assertEquals(i18n.tr("Unexpected data error from Cloud Registration Service: {0}", message),
            assertThrows(CandlepinException.class,
                () -> CloudRegistrationServiceExceptionMapper.map(
                    new CloudRegistrationUnknownDataException(message),
                    type, i18n))
                .getMessage());
        assertEquals(i18n.tr("Unexpected error from Cloud Registration Service: {0}", message),
            assertThrows(CandlepinException.class,
                () -> CloudRegistrationServiceExceptionMapper.map(
                    new CloudRegistrationServiceException(message),
                    type, i18n))
                .getMessage());
    }
}
