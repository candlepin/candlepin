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
package org.candlepin.exceptions.mappers;

import org.candlepin.service.exception.ServiceException;
import org.candlepin.service.exception.cloudregistration.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.cloudregistration.CloudRegistrationMalformedDataException;
import org.candlepin.service.exception.cloudregistration.CloudRegistrationServiceException;
import org.candlepin.service.exception.product.ProductServiceException;
import org.candlepin.service.exception.subscription.SubscriptionActivationException;
import org.candlepin.service.exception.subscription.SubscriptionExhaustedTagException;
import org.candlepin.service.exception.subscription.SubscriptionExpiredTagException;
import org.candlepin.service.exception.subscription.SubscriptionInvalidTagException;
import org.candlepin.service.exception.subscription.SubscriptionServiceException;
import org.candlepin.service.exception.user.UserDisabledException;
import org.candlepin.service.exception.user.UserInvalidException;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.service.exception.user.UserUnacceptedTermsException;
import org.candlepin.service.exception.user.UserUnauthorizedException;

import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * ServiceExceptionMapper maps the RESTEasy Failure into JSON and allows the
 * proper header to be set. This allows Candlepin to control the flow of the
 * exceptions.
 */
@Provider
public class ServiceExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<ServiceException> {

    @Override
    public Response toResponse(ServiceException exception) {
        Status status;
        String message = exception.getMessage();

        // TODO: once switch expressions are available we should convert this logic
        if (exception instanceof SubscriptionActivationException sae) {
            status = Status.EXPECTATION_FAILED;
            message = i18n.get().tr(
                "No subscription was able to be activated with Dell service tag \"{0}\".",
                sae.getDellAssetTag());
        }
        else if (exception instanceof SubscriptionExhaustedTagException sete) {
            status = Status.EXPECTATION_FAILED;
            message = i18n.get().tr(
                "The Dell service tag \"{0}\" has already been used to redeem a subscription.",
                sete.getDellAssetTag());
        }
        else if (exception instanceof SubscriptionExpiredTagException sete) {
            status = Status.EXPECTATION_FAILED;
            message = i18n.get().tr(
                "The Dell service tag \"{0}\" is expired.",
                sete.getDellAssetTag());
        }
        else if (exception instanceof SubscriptionInvalidTagException site) {
            status = Status.EXPECTATION_FAILED;
            message = i18n.get().tr(
                "The Dell service tag \"{0}\" could not be used to retrieve a subscription.",
                site.getDellAssetTag());
        }
        else if (exception instanceof SubscriptionServiceException) {
            status = Status.EXPECTATION_FAILED;
            message = i18n.get().tr(
                "Unexpected error from Subscription Service: {0}", exception.getMessage());
        }
        else if (exception instanceof CloudRegistrationAuthorizationException) {
            status = Status.UNAUTHORIZED;
            message = i18n.get().tr(
                "Cloud provider or account details could not be resolved to an organization");
        }
        else if (exception instanceof CloudRegistrationMalformedDataException) {
            status = Status.BAD_REQUEST;
            // Some tests return a message. Will retain it instead.
            message = !StringUtils.isEmpty(message) ? message : i18n.get().tr(
                "Unable to complete Cloud Registration with provided data");
        }
        else if (exception instanceof CloudRegistrationServiceException) {
            status = Status.BAD_REQUEST;
            message = i18n.get().tr(
                "Unexpected error from Cloud Registration Service: {0}", exception.getMessage());
        }
        else if (exception instanceof UserInvalidException uie) {
            status = Status.UNAUTHORIZED;
            message = i18n.get().tr("User \"{0}\" is not valid.", uie.getUsername());
        }
        else if (exception instanceof UserUnacceptedTermsException) {
            status = Status.UNAUTHORIZED;
            message = i18n.get().tr("You must first accept Red Hat''s Terms and conditions. " +
                "Please visit {0} . You may have to log out of and back into the  " +
                "Customer Portal in order to see the terms.",
                "https://www.redhat.com/wapps/ugc");
        }
        else if (exception instanceof UserDisabledException ude) {
            status = Status.UNAUTHORIZED;
            message = i18n.get().tr("The user \"{0}\" has been disabled, if this is a mistake, " +
                "please contact customer service.", ude.getUsername());
        }
        else if (exception instanceof UserUnauthorizedException) {
            status = Status.UNAUTHORIZED;
            message = i18n.get().tr("Invalid username or password. To create a login, " +
                "please visit {0}", "https://www.redhat.com/wapps/ugc/register.html");
        }
        else if (exception instanceof UserServiceException) {
            status = Status.UNAUTHORIZED;
            message = i18n.get().tr("Unexpected error from User Service: {0}", exception.getMessage());
        }
        else if (exception instanceof ProductServiceException pse) {
            status = Status.BAD_REQUEST;
            message = i18n.get().tr("Unable to retrieve product \"{0}\" from ProductService",
                pse.getProductId());
        }
        else {
            status = Status.BAD_REQUEST;
            message = i18n.get().tr("Unknown service exception");
        }
        return getDefaultBuilder(new ServiceException(message, exception), status, determineBestMediaType())
            .build();
    }
}
