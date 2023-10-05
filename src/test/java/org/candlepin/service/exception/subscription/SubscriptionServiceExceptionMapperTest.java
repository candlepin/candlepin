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
package org.candlepin.service.exception.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.TestingModules;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;



public class SubscriptionServiceExceptionMapperTest {

    I18n i18n;

    @BeforeEach
    public void init() {
        Injector injector = Guice.createInjector(
            new TestingModules.ServletEnvironmentModule());
        i18n = injector.getInstance(I18n.class);
    }

    @Test
    public void exceptionMappingTest() {
        assertEquals(
            i18n.tr("A subscription was not found for the given Dell service tag: {0}", "service_tag"),
            SubscriptionServiceExceptionMapper.map(new SubscriptionMissingTagException(),
                "service_tag", "owner_key", i18n));
        assertEquals(i18n.tr("The Dell service tag: {0}, is expired", "service_tag"),
            SubscriptionServiceExceptionMapper.map(new SubscriptionExpiredTagException(),
                "service_tag", "owner_key", i18n));
        assertEquals(i18n.tr("No admins were returned for owner {0}", "owner_key"),
            SubscriptionServiceExceptionMapper.map(new SubscriptionActivationNoAdminsException(),
                "service_tag", "owner_key", i18n));
        assertEquals(i18n.tr("The system is unable to redeem the requested subscription: {0}", "service_tag"),
            SubscriptionServiceExceptionMapper.map(new SubscriptionUnknownActivationException(),
                "service_tag", "owner_key", i18n));
        assertEquals(
            i18n.tr("The Dell service tag: {0}, has already been used to redeem a subscription",
                "service_tag"),
            SubscriptionServiceExceptionMapper.map(new SubscriptionUsedTagException(),
                "service_tag", "owner_key", i18n));
        assertEquals(
            i18n.tr("No subscription was activated because there is no Dell Asset Tag", "service_tag"),
            SubscriptionServiceExceptionMapper.map(new SubscriptionActivationNoTagException(),
                "service_tag", "owner_key", i18n));
        assertEquals(i18n.tr("The system is unable to redeem the requested subscription: {0}", "service_tag"),
            SubscriptionServiceExceptionMapper.map(new SubscriptionActivationNoRedemptionTagException(),
                "service_tag", "owner_key", i18n));
        assertEquals(i18n.tr("The Dell service tag: {0}, could not be found.", "service_tag"),
            SubscriptionServiceExceptionMapper.map(new SubscriptionNotFoundTagException(),
                "service_tag", "owner_key", i18n));
        String message = "oops";
        assertEquals(i18n.tr("Unexpected error from Subscription Service: {0}", message),
            SubscriptionServiceExceptionMapper.map(new SubscriptionServiceException(message),
                "service_tag", "owner_key", i18n));
    }
}
