/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.resteasy.filter;

import org.candlepin.auth.ActivationKeyPrincipal;
import org.candlepin.auth.AnonymousCloudConsumerPrincipal;
import org.candlepin.auth.CloudConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SecurityHole;
import org.candlepin.exceptions.TooManyRequestsException;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotCreatedYetException;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotEntitledYetException;

import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.lang.reflect.Method;
import java.util.Objects;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;

/**
 * SecurityHoleAuthorizationFilter is a no-op JAX-RS 2.0 Filter that is applied
 * to methods that have the SecurityHole annotation applied to them.  The
 * AuthorizationFeature class is what determines whether to register this filter to
 * a method.
 */
@Priority(Priorities.AUTHORIZATION)
public class SecurityHoleAuthorizationFilter extends AbstractAuthorizationFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHoleAuthorizationFilter.class);

    // The Org does not exist at all in the backend services upstream of Candlepin yet
    public static final int ORG_DOES_NOT_EXIST_AT_ALL = 300;

    // The Org exists upstream of Candlepin, but does not have SKU(s) subscribed to it yet
    public static final int ORG_IS_NOT_ENTITLED = 180;

    // The Org exists upstream of Candlepin, and has the necessary subscriptions there,
    // but it is not yet created in Candlepin
    public static final int ORG_NOT_CREATED_IN_CANDLEPIN = 60;

    // The Org exists in Candlepin, but the necessary pools to satisfy the client have not been created yet
    public static final int ORG_DOES_NOT_HAVE_POOLS = 30;


    private final AnnotationLocator annotationLocator;
    private final OwnerCurator ownerCurator;
    private final PoolCurator poolCurator;
    private final CloudRegistrationAdapter cloudAdapter;

    @Inject
    public SecurityHoleAuthorizationFilter(Provider<I18n> i18nProvider, AnnotationLocator annotationLocator,
        OwnerCurator ownerCurator, PoolCurator poolCurator, CloudRegistrationAdapter cloudAdapter) {

        this.i18nProvider = i18nProvider;
        this.annotationLocator = Objects.requireNonNull(annotationLocator);
        this.ownerCurator = ownerCurator;
        this.poolCurator = poolCurator;
        this.cloudAdapter = cloudAdapter;
    }

    @Override
    void runFilter(ContainerRequestContext requestContext) {
        log.debug("NO authorization check for {}", requestContext.getUriInfo().getPath());

        Principal principal = (Principal) requestContext.getSecurityContext().getUserPrincipal();

        ResourceInfo resourceInfo = ResteasyContext.getContextData(ResourceInfo.class);
        Method method = resourceInfo.getResourceMethod();
        SecurityHole securityHole = annotationLocator.getAnnotation(method, SecurityHole.class);

        if (principal instanceof ActivationKeyPrincipal) {
            if (!securityHole.activationKey()) {
                denyAccess(principal, method);
            }
        }
        if (principal instanceof AnonymousCloudConsumerPrincipal anonymPrincipal) {
            if (!securityHole.autoregToken()) {
                denyAccess(principal, method);
            }
            validateAnonymousCloudConsumer(anonymPrincipal);
        }
        if (principal instanceof CloudConsumerPrincipal) {
            if (!securityHole.autoregToken()) {
                denyAccess(principal, method);
            }
        }
    }

    private void validateAnonymousCloudConsumer(AnonymousCloudConsumerPrincipal principal) {
        AnonymousCloudConsumer anonymousCloudConsumer = principal.getAnonymousCloudConsumer();
        String ownerKey = checkCloudAccountOrganization(anonymousCloudConsumer);
        if (!ownerCurator.existsByKey(ownerKey)) {
            throw new TooManyRequestsException(i18nProvider.get().tr("Owner does not yet exist"))
                .setRetryAfterTime(ORG_NOT_CREATED_IN_CANDLEPIN);
        }
        if (!poolCurator.hasPoolsForProducts(ownerKey, anonymousCloudConsumer.getProductIds())) {
            throw new TooManyRequestsException(i18nProvider.get().tr("Owner is not yet entitled"))
                .setRetryAfterTime(ORG_DOES_NOT_HAVE_POOLS);
        }
        anonymousCloudConsumer.setOwnerKey(ownerKey);
    }

    private String checkCloudAccountOrganization(AnonymousCloudConsumer anonymousCloudConsumer) {
        try {
            return cloudAdapter.checkCloudAccountOrgIsReady(
                anonymousCloudConsumer.getCloudAccountId(),
                anonymousCloudConsumer.getCloudProviderShortName(),
                anonymousCloudConsumer.getCloudOfferingId());
        }
        catch (OrgForCloudAccountNotCreatedYetException e) {
            throw new TooManyRequestsException(i18nProvider.get()
                .tr("Anonymous owner not yet created upstream of candlepin"))
                .setRetryAfterTime(ORG_DOES_NOT_EXIST_AT_ALL);
        }
        catch (OrgForCloudAccountNotEntitledYetException e) {
            throw new TooManyRequestsException(i18nProvider.get()
                .tr("Anonymous owner not yet entitled upstream of candlepin"))
                .setRetryAfterTime(ORG_IS_NOT_ENTITLED);
        }
    }
}
