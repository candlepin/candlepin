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
package org.fedoraproject.candlepin.policy;

import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.policy.java.JavaEnforcer;
import org.fedoraproject.candlepin.policy.java.JavaPostEntitlementProcessor;

import com.google.inject.Inject;

public class PolicyFactory {
    
    private JavaPostEntitlementProcessor postEntProcessor;
    
    @Inject
    public PolicyFactory(JavaPostEntitlementProcessor postEntProcessor) {
        this.postEntProcessor = postEntProcessor;
    }

    // TODO: Inject this as well?
    public Enforcer createEnforcer(DateSource dateSource, EntitlementPoolCurator epCurator) {
        return new JavaEnforcer(dateSource);
    }
    
    public PostEntitlementProcessor createPostEntitlementProcessor() {
        return postEntProcessor;
    }

}
