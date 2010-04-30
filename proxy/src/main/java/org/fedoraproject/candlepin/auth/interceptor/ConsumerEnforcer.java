/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.auth.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Interceptor for enforcing that a referenced consumer UUID can
 * be accessed by the current {@link Principal}.
 */
public class ConsumerEnforcer implements MethodInterceptor {

    @Inject private ConsumerCurator consumerCurator;
    @Inject private Provider<Principal> principalProvider;
    @Inject private Provider<I18n> i18nProvider;
    
    private static Logger log = Logger.getLogger(ConsumerEnforcer.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Principal currentUser = this.principalProvider.get();
        log.debug("Invoked.");
        
        // First make sure this user has the Consumer role:
        if (!currentUser.getRoles().contains(Role.CONSUMER)) {
            I18n i18n = this.i18nProvider.get();
            
            String error = "Insufficient permission";
            throw new ForbiddenException(i18n.tr(error));
        }
        
        // Do we need to verify this consumer is accessing only their own data, or is this
        // just a straight role check:
        EnforceConsumer annotation = invocation.getMethod().getAnnotation(
            EnforceConsumer.class);
        if (!annotation.pathParam().equals("") || !annotation.queryParam().equals("")) {
            log.debug("Checking for form/query params.");
            verifyPathParams(currentUser, invocation, annotation);
        }
        
        return invocation.proceed();
        
    }
    
    private void verifyPathParams(Principal currentUser, MethodInvocation invocation, 
        EnforceConsumer annotation) {
        
        Consumer requestedConsumer = getViewedConsumer(invocation, annotation);

        if (!currentUser.canAccessConsumer(requestedConsumer)) {
            I18n i18n = this.i18nProvider.get();
            
            String error = "You do not have permission to access consumer: {0}";
            throw new ForbiddenException(i18n.tr(error, requestedConsumer.getUuid()));
        }
    }

    private Consumer getViewedConsumer(MethodInvocation invocation, 
        EnforceConsumer annotation) throws NotFoundException {
        
        String consumerUuid = getViewedConsumerUuid(invocation, annotation);
        Consumer consumer = this.consumerCurator.lookupByUuid(consumerUuid);
        
        if (consumer == null) {
            I18n i18n = this.i18nProvider.get();
            throw new NotFoundException(i18n.tr("No such consumer: {0}", consumerUuid));
        }
        
        return this.consumerCurator.lookupByUuid(consumerUuid);
    }

    /**
     * Get the consumer uuid that is being requested in this method invocation.
     *
     * @param invocation
     * @return
     */
    private String getViewedConsumerUuid(MethodInvocation invocation, 
        EnforceConsumer annotation) {
        
        // One of these must be set to something other than empty string to reach 
        // this point:
        String pathParam = annotation.pathParam();
        String queryParam = annotation.queryParam();
        
        String consumerUuid = null;
        
        if (pathParam != "") {
            log.debug("Checking path param.");
            int paramIndex = getIndexOfPathParam(invocation.getMethod(), "path", 
                pathParam);
            if (paramIndex > -1 && paramIndex < invocation.getArguments().length) {
                consumerUuid = (String) invocation.getArguments()[paramIndex];
            }
        }
        else if (queryParam != "") {
            log.debug("Checking query param.");
            int paramIndex = getIndexOfPathParam(invocation.getMethod(), "query", 
                queryParam);
            if (paramIndex > -1 && paramIndex < invocation.getArguments().length) {
                consumerUuid = (String) invocation.getArguments()[paramIndex];
            }
        }

        return consumerUuid;
    }

    private int getIndexOfPathParam(Method method, String paramAnnotation, 
        String paramName) {
        for (int i = 0; i < method.getParameterAnnotations().length; i++) {
            
            // Trying to use the same code for both types of param checks:
            for (Annotation annotation : method.getParameterAnnotations()[i]) {
                if (paramAnnotation.equals("path") && annotation instanceof PathParam) {
                    if (paramName.equals(((PathParam) annotation).value())) {
                        return i;
                    }
                }
                else if (paramAnnotation.equals("query") && 
                    annotation instanceof QueryParam) {
                    if (paramName.equals(((QueryParam) annotation).value())) {
                        return i;
                    }
                }

            }
        }

        // should probably throw an exception here...
        return -1;
    }
    
}
