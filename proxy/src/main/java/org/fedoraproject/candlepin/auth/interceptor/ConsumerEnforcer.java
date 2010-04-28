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

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.fedoraproject.candlepin.auth.Principal;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Consumer requestedConsumer = getViewedConsumer(invocation);
        Principal currentUser = this.principalProvider.get();

        if (!currentUser.canAccessConsumer(requestedConsumer)) {
            I18n i18n = this.i18nProvider.get();
            
            String error = "You do not have permission to access consumer: {0}";
            throw new ForbiddenException(i18n.tr(error, requestedConsumer.getUuid()));
        }

        return invocation.proceed();
    }

    private Consumer getViewedConsumer(MethodInvocation invocation) 
        throws NotFoundException {
        
        String consumerUuid = getViewedConsumerUuid(invocation);
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
    private String getViewedConsumerUuid(MethodInvocation invocation) {
        String consumerParameter = getConsumerParameter(invocation.getMethod());
        int paramIndex = getIndexOfPathParam(invocation.getMethod(), consumerParameter);

        if (paramIndex > -1 && paramIndex < invocation.getArguments().length) {
            return (String) invocation.getArguments()[paramIndex];
        }

        return null;
    }

    private String getConsumerParameter(Method method) {
        EnforceConsumer annotation = method.getAnnotation(EnforceConsumer.class);
        return annotation.pathParam();
    }

    private int getIndexOfPathParam(Method method, String paramName) {
        for (int i = 0; i < method.getParameterAnnotations().length; i++) {
            for (Annotation annotation : method.getParameterAnnotations()[i]) {
                if (annotation instanceof PathParam) {
                    if (paramName.equals(((PathParam) annotation).value())) {
                        return i;
                    }
                }
            }
        }

        // should probably throw an exception here...
        return -1;
    }
}
