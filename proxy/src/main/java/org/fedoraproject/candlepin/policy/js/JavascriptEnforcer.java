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
package org.fedoraproject.candlepin.policy.js;


import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationError;

import com.google.inject.Inject;

public class JavascriptEnforcer implements Enforcer {
    
    private static Logger log = Logger.getLogger(JavascriptEnforcer.class);
    private Rules rules;
    private DateSource dateSource;
    private EntitlementPoolCurator epCurator;
    private ProductCurator prodCurator;
    private RulesCurator rulesCurator;
    private PreEntHelper preHelper;
    private PostEntHelper postHelper;

    
    @Inject
    public JavascriptEnforcer(DateSource dateSource, EntitlementPoolCurator epCurator,
            ProductCurator prodCurator, RulesCurator rulesCurator, PreEntHelper preHelper,
            PostEntHelper postHelper) {
        this.dateSource = dateSource;
        this.epCurator = epCurator;
        this.prodCurator = prodCurator;
        this.rulesCurator = rulesCurator;
        this.preHelper = preHelper;
        this.postHelper = postHelper;

        this.rules = new Rules(this.rulesCurator.getRules().getRules());
    }


    @Override
    public PreEntHelper pre(Consumer consumer, EntitlementPool entitlementPool) {

        rules.runPre(preHelper, consumer, entitlementPool);
        if (!preHelper.getResult().isSuccessful()) {
//            throw new
        }

        if (entitlementPool.isExpired(dateSource)) {
            preHelper.getResult().addError(new ValidationError("Entitlements for " +
                    entitlementPool.getProduct().getName() +
                    " expired on: " + entitlementPool.getEndDate()));
            return preHelper;
        }

        return preHelper;
    }

    @Override
    public PostEntHelper post(Entitlement ent) {
        postHelper.init(ent);
        rules.runPost(postHelper, ent);
        return(postHelper);
    }

}
