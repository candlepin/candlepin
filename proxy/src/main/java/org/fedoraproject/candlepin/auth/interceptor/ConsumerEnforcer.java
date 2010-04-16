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

import com.google.inject.Inject;
import com.google.inject.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import javax.ws.rs.PathParam;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.resource.ForbiddenException;

/**
 * Interceptor for enforcing that a referenced consumer UUID can
 * be accessed by the current {@link Principal}.
 */
public class ConsumerEnforcer implements MethodInterceptor {

    private Provider<Principal> principalProvider;

    @Inject
    public void setPrincipalProvider(Provider<Principal> principalProvider) {
        this.principalProvider = principalProvider;
    }

    /**
     * {@inheritDoc}
     *
     * @param invocation
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String consumerUuid = getViewedConsumerUuid(invocation);
        Principal currentUser = this.principalProvider.get();

        if (!currentUser.canAccessConsumer(consumerUuid)) {
            // TODO:  Integrate with the new i18n stuff
            String error = "You do not have permission to access this consumer.";
            throw new ForbiddenException(error);
        }

        return invocation.proceed();
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
