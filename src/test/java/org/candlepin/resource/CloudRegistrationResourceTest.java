/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;

import org.candlepin.auth.CloudRegistrationAuth;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotAuthorizedException;
import org.candlepin.common.exceptions.NotImplementedException;
import org.candlepin.dto.api.v1.CloudRegistrationDTO;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.exception.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.MalformedCloudRegistrationException;
import org.candlepin.service.model.CloudRegistrationInfo;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;


/**
 * Test suite for the CloudRegistrationResource class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CloudRegistrationResourceTest {

    private I18n i18n;
    private CloudRegistrationAuth mockCloudRegistrationAuth;
    private Principal principal;
    private DTOValidator mockValidator;


    @BeforeEach
    public void init() {
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.mockCloudRegistrationAuth = mock(CloudRegistrationAuth.class);
        this.mockValidator = mock(DTOValidator.class);
        this.principal = this.buildPrincipal();
        ResteasyContext.pushContext(Principal.class, this.principal);
    }

    private CloudRegistrationResource buildResource() {
        return new CloudRegistrationResource(this.i18n, this.mockCloudRegistrationAuth, this.mockValidator);
    }

    private Principal buildPrincipal() {
        return new UserPrincipal("test_user", null, false);
    }

    @Test
    public void testAuthorize() {
        String token = "test-token";

        CloudRegistrationResource resource = this.buildResource();
        Principal principal = this.buildPrincipal();

        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type("test-type")
            .metadata("test-metadata")
            .signature("test-signature");

        doReturn(token).when(this.mockCloudRegistrationAuth)
            .generateRegistrationToken(eq(principal), any(CloudRegistrationInfo.class));

        String output = resource.cloudAuthorize(dto);

        assertEquals(token, output);
    }

    @Test
    public void testAuthorizeFailsGracefullyWhenNotImplemented() {
        CloudRegistrationResource resource = this.buildResource();

        doThrow(new UnsupportedOperationException()).when(this.mockCloudRegistrationAuth)
            .generateRegistrationToken(eq(principal), any(CloudRegistrationInfo.class));

        assertThrows(NotImplementedException.class,
            () -> resource.cloudAuthorize(new CloudRegistrationDTO()));
    }

    @Test
    public void testAuthorizeFailsGracefullyWhenAuthenticationFails() {
        CloudRegistrationResource resource = this.buildResource();

        doThrow(new CloudRegistrationAuthorizationException()).when(this.mockCloudRegistrationAuth)
            .generateRegistrationToken(eq(principal), any(CloudRegistrationInfo.class));

        assertThrows(NotAuthorizedException.class,
            () -> resource.cloudAuthorize(new CloudRegistrationDTO()));
    }

    @Test
    public void testAuthorizeFailsGracefullyWithMalformedInput() {
        CloudRegistrationResource resource = this.buildResource();

        doThrow(new MalformedCloudRegistrationException()).when(this.mockCloudRegistrationAuth)
            .generateRegistrationToken(eq(principal), any(CloudRegistrationInfo.class));

        assertThrows(BadRequestException.class,
            () -> resource.cloudAuthorize(new CloudRegistrationDTO()));
    }

}
