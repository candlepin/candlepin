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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.exception.user.UserDisabledException;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.ws.rs.core.Response.Status;

/**
 * ProductResourceTest
 */
public class RoleResourceTest extends DatabaseTestFixture {

    @Mock
    private UserServiceAdapter mockUserServiceAdapter;

    private DTOValidator dtoValidator;
    private RoleResource roleResource;

    @BeforeEach
    public void init() throws Exception {
        super.init();
        MockitoAnnotations.initMocks(this);

        this.dtoValidator = new DTOValidator(this.i18n);
        this.roleResource = new RoleResource(this.mockUserServiceAdapter, this.ownerCurator,
            this.permissionBlueprintCurator, this.i18n, this.modelTranslator, this.dtoValidator);
    }

    @Test
    public void fetchUserByUsernameExceptions() {
        when(this.mockUserServiceAdapter.findByLogin(anyString())).thenReturn(null);
        assertThrows(NotFoundException.class, () -> roleResource.fetchUserByUsername("test_user"));

        when(this.mockUserServiceAdapter.findByLogin(anyString())).thenThrow(UserDisabledException.class);
        assertThrows(NotAuthorizedException.class, () -> roleResource.fetchUserByUsername("test_user"));
    }

    @Test
    public void testUnexpectedUserLookupFailureReturnsUnauthorized() {
        UserServiceException failure = new UserServiceException("unavailable");
        when(this.mockUserServiceAdapter.findByLogin("test_user")).thenThrow(failure);

        CandlepinException result = assertThrows(CandlepinException.class,
            () -> this.roleResource.fetchUserByUsername("test_user"));

        assertThat(result)
            .returns(Status.UNAUTHORIZED, CandlepinException::httpReturnCode)
            .returns("Error contacting user service", CandlepinException::getMessage);
        assertSame(failure, result.getCause());
    }
}
