package org.fedoraproject.candlepin.model;

import java.util.List;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

public class RulesCurator extends AbstractHibernateCurator<Rules> {

    protected RulesCurator() {
        super(Rules.class);
    }
    
    
    @Transactional
    // This seems lame...
    public Rules update(Rules updatedRules) {
        List<Rules> existingRuleSet = findAll();
        if (existingRuleSet.size() == 0) {
            return create(updatedRules);
        }
        for (Rules rule: existingRuleSet) {
            delete(rule);
        }
        create(updatedRules);
        return updatedRules;
        
    }
    
    
}
