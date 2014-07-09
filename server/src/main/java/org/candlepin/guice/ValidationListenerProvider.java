/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.guice;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;

import java.util.Properties;

import javax.validation.ValidatorFactory;

/**
 * ValidationListenerFactory
 */
public class ValidationListenerProvider implements Provider<BeanValidationEventListener> {

    private ValidatorFactory factory;
    private Properties properties;

    @Inject
    public ValidationListenerProvider(ValidatorFactory factory,
        @Named("ValidationProperties") Properties properties) {
        this.factory = factory;
        this.properties = properties;
    }

    @Override
    public BeanValidationEventListener get() {
        return new BeanValidationEventListener(factory, properties);
    }
}
