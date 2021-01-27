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

import org.candlepin.auth.CloudRegistrationAuth;
import org.candlepin.auth.Principal;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotAuthorizedException;
import org.candlepin.common.exceptions.NotImplementedException;
import org.candlepin.dto.api.v1.CloudRegistrationDTO;
import org.candlepin.service.exception.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.MalformedCloudRegistrationException;


import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.Objects;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;



/**
 * End point(s) for cloud registration token generation
 */
@Path("/cloud")
public class CloudRegistrationResource {
    private final CloudRegistrationAuth cloudRegistrationAuth;

    private final I18n i18n;

    @Inject
    public CloudRegistrationResource(I18n i18n, CloudRegistrationAuth cloudRegistrationAuth) {
        this.i18n = Objects.requireNonNull(i18n);
        this.cloudRegistrationAuth = Objects.requireNonNull(cloudRegistrationAuth);
    }

    @POST
    @Path("authorize")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    @SecurityHole(noAuth = true)
    public String authorize(CloudRegistrationDTO cloudRegDTO,
        @Context Principal principal) {

        if (cloudRegDTO == null) {
            throw new BadRequestException(this.i18n.tr("No cloud registration information provided"));
        }

        try {
            return this.cloudRegistrationAuth.generateRegistrationToken(principal, cloudRegDTO);
        }
        catch (UnsupportedOperationException e) {
            String errmsg = this.i18n.tr("Cloud registration is not supported by this Candlepin instance");
            throw new NotImplementedException(errmsg);
        }
        catch (CloudRegistrationAuthorizationException e) {
            throw new NotAuthorizedException(e.getMessage());
        }
        catch (MalformedCloudRegistrationException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
