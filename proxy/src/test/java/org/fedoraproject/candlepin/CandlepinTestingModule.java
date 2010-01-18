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
package org.fedoraproject.candlepin;

import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.guice.JPAInitializer;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.PostEntitlementProcessor;
import org.fedoraproject.candlepin.policy.java.JavaEnforcer;
import org.fedoraproject.candlepin.policy.java.JavaPostEntitlementProcessor;
import org.fedoraproject.candlepin.test.DateSourceForTesting;

import com.google.inject.AbstractModule;
import com.wideplay.warp.persist.jpa.JpaUnit;

public class CandlepinTestingModule extends AbstractModule {

    @Override
    public void configure() {
        
        bind(JPAInitializer.class).asEagerSingleton();
        bindConstant().annotatedWith(JpaUnit.class).to("test");
        
        bind(DateSource.class).to(DateSourceForTesting.class).asEagerSingleton();
        bind(Enforcer.class).to(JavaEnforcer.class);
        bind(PostEntitlementProcessor.class).to(JavaPostEntitlementProcessor.class);
    }
}
