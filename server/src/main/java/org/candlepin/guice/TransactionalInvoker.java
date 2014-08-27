package org.candlepin.guice;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.aopalliance.intercept.MethodInvocation;

public class TransactionalInvoker {

    @Inject
    public TransactionalInvoker() {
    }

    @Transactional
    public Object invoke(MethodInvocation invocation) throws Throwable {
        return invocation.proceed();
    }
}
