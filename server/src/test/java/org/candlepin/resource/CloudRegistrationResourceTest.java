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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.auth.CloudRegistrationAuth;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotAuthorizedException;
import org.candlepin.common.exceptions.NotImplementedException;
import org.candlepin.dto.api.v1.CloudRegistrationDTO;
import org.candlepin.service.exception.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.MalformedCloudRegistrationException;
import org.candlepin.service.model.CloudRegistrationInfo;

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


    @BeforeEach
    public void init() {
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.mockCloudRegistrationAuth = mock(CloudRegistrationAuth.class);
    }

    private CloudRegistrationResource buildResource() {
        return new CloudRegistrationResource(this.i18n, this.mockCloudRegistrationAuth);
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
            .setType("test-type")
            .setMetadata("test-metadata")
            .setSignature("test-signature");

        doReturn(token).when(this.mockCloudRegistrationAuth)
            .generateRegistrationToken(eq(principal), eq(dto));

        String output = resource.authorize(dto, principal);

        assertEquals(token, output);
    }

    @Test
    public void testAuthorizeFailsGracefullyWhenNotImplemented() {
        CloudRegistrationResource resource = this.buildResource();
        Principal principal = this.buildPrincipal();

        doThrow(new UnsupportedOperationException()).when(this.mockCloudRegistrationAuth)
            .generateRegistrationToken(eq(principal), any(CloudRegistrationInfo.class));

        assertThrows(NotImplementedException.class,
            () -> resource.authorize(new CloudRegistrationDTO(), principal));
    }

    @Test
    public void testAuthorizeFailsGracefullyWhenAuthenticationFails() {
        CloudRegistrationResource resource = this.buildResource();
        Principal principal = this.buildPrincipal();

        doThrow(new CloudRegistrationAuthorizationException()).when(this.mockCloudRegistrationAuth)
            .generateRegistrationToken(eq(principal), any(CloudRegistrationInfo.class));

        assertThrows(NotAuthorizedException.class,
            () -> resource.authorize(new CloudRegistrationDTO(), principal));
    }

    @Test
    public void testAuthorizeFailsGracefullyWithMalformedInput() {
        CloudRegistrationResource resource = this.buildResource();
        Principal principal = this.buildPrincipal();

        doThrow(new MalformedCloudRegistrationException()).when(this.mockCloudRegistrationAuth)
            .generateRegistrationToken(eq(principal), any(CloudRegistrationInfo.class));

        assertThrows(BadRequestException.class,
            () -> resource.authorize(new CloudRegistrationDTO(), principal));
    }

}
