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

import static org.junit.jupiter.api.Assertions.assertEquals;

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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.stream.Stream;

import javax.ws.rs.core.Response;


/**
 * FailureExceptionMapperTest
 */
public class ServiceExceptionMapperTest extends TestExceptionMapperBase {

    private static final I18n I18N = I18nFactory.getI18n(ServiceExceptionMapperTest.class,
        Locale.US, I18nFactory.FALLBACK);

    public static Stream<Arguments> exceptionMappings() {
        String serviceTag = "service_tag";
        String ownerKey = "owner_key";
        String message = "oops";
        String username = "test_user";
        String productId = "test_product";
        return Stream.of(
            Arguments.of(new SubscriptionActivationException(serviceTag, ownerKey), 417,
                I18N.tr("No subscription was able to be activated with Dell service tag \"{0}\".",
                    serviceTag)),
            Arguments.of(new SubscriptionExhaustedTagException(serviceTag, ownerKey), 417,
                I18N.tr("The Dell service tag \"{0}\" has already been used to redeem a subscription.",
                    serviceTag)),
            Arguments.of(new SubscriptionExpiredTagException(serviceTag, ownerKey), 417,
                I18N.tr("The Dell service tag \"{0}\" is expired.",
                    serviceTag)),
            Arguments.of(new SubscriptionInvalidTagException(serviceTag, ownerKey), 417,
                I18N.tr("The Dell service tag \"{0}\" could not be used to retrieve a subscription.",
                    serviceTag)),
            Arguments.of(new SubscriptionServiceException(message), 417,
                I18N.tr("Unexpected error from Subscription Service: {0}", message)),
            Arguments.of(new CloudRegistrationAuthorizationException(), 401,
                I18N.tr("Cloud provider or account details could not be resolved to an organization")),
            Arguments.of(new CloudRegistrationMalformedDataException(), 400,
                I18N.tr("Unable to complete Cloud Registration with provided data")),
            Arguments.of(new CloudRegistrationServiceException(message), 400,
                I18N.tr("Unexpected error from Cloud Registration Service: {0}", message)),
            Arguments.of(new UserInvalidException(username), 401,
                I18N.tr("User \"{0}\"' is not valid.", username)),
            Arguments.of(new UserUnacceptedTermsException(username), 401,
                I18N.tr("You must first accept Red Hat''s Terms and conditions. " +
                        "Please visit {0} . You may have to log out of and back into the  " +
                        "Customer Portal in order to see the terms.",
                    "https://www.redhat.com/wapps/ugc")),
            Arguments.of(new UserDisabledException(username), 401,
                I18N.tr("The user \"{0}\" has been disabled, if this is a mistake, " +
                    "please contact customer service.", username)),
            Arguments.of(new UserUnauthorizedException(username), 401,
                I18N.tr("Invalid username or password. To create a login, please visit {0}",
                    "https://www.redhat.com/wapps/ugc/register.html")),
            Arguments.of(new UserServiceException(message), 401,
                I18N.tr("Unexpected error from User Service: {0}", message)),
            Arguments.of(new ProductServiceException(productId), 400,
                I18N.tr("Unable to retrieve product \"{0}\" from ProductService", productId))
        );
    }



    @ParameterizedTest
    @MethodSource("exceptionMappings")
    public void handleExceptionWithoutResponse(ServiceException se, int status, String message) {
        ServiceExceptionMapper nfem =
            injector.getInstance(ServiceExceptionMapper.class);
        Response r = nfem.toResponse(se);
        assertEquals(status, r.getStatus());
        verifyMessage(r, rtmsg(message));
    }

    public Class<?> getMapperClass() {
        return ServiceExceptionMapper.class;
    }
}
