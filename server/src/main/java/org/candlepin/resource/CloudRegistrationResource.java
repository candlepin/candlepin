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
import org.candlepin.auth.CloudRegistrationData;
import org.candlepin.auth.Principal;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotAuthorizedException;
import org.candlepin.common.exceptions.NotImplementedException;
import org.candlepin.dto.api.v1.CloudRegistrationDTO;
import org.candlepin.service.exception.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.MalformedCloudRegistrationException;

import com.google.inject.Inject;

import org.jboss.resteasy.core.ResteasyContext;
import org.xnap.commons.i18n.I18n;

import java.util.Objects;


/**
 * End point(s) for cloud registration token generation
 */
public class CloudRegistrationResource implements CloudApi {
    private final CloudRegistrationAuth cloudRegistrationAuth;
    private final I18n i18n;

    @Inject
    public CloudRegistrationResource(I18n i18n, CloudRegistrationAuth cloudRegistrationAuth) {
        this.i18n = Objects.requireNonNull(i18n);
        this.cloudRegistrationAuth = Objects.requireNonNull(cloudRegistrationAuth);
    }

    @Override
    @SecurityHole(noAuth = true)
    public String cloudAuthorize(CloudRegistrationDTO cloudRegistrationDTO) {

        if (cloudRegistrationDTO == null) {
            throw new BadRequestException(this.i18n.tr("No cloud registration information provided"));
        }

        Principal principal = ResteasyContext.getContextData(Principal.class);
        CloudRegistrationData registrationData = getCloudRegistrationData(cloudRegistrationDTO);
        try {
            return this.cloudRegistrationAuth.generateRegistrationToken(principal, registrationData);
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

    private CloudRegistrationData getCloudRegistrationData(CloudRegistrationDTO cloudRegistrationDTO) {
        CloudRegistrationData registrationData = new CloudRegistrationData();
        registrationData.setType(cloudRegistrationDTO.getType());
        registrationData.setMetadata(cloudRegistrationDTO.getMetadata());
        registrationData.setSignature(cloudRegistrationDTO.getSignature());
        return registrationData;
    }
}
