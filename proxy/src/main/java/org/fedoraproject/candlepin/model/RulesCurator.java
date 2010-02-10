package org.fedoraproject.candlepin.model;

import com.wideplay.warp.persist.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

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
    
    private void initiateRulesFromFile() {
        String path = "/rules/satellite-rules.js";
        InputStream is = path.getClass().getResourceAsStream(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line + "\n");
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Rules rules = new Rules(builder.toString());
        create(rules);
    }
    
    public Rules getRules() {
        List<Rules> existingRuleSet = findAll();
        if (existingRuleSet.size() == 0) {
            initiateRulesFromFile();
            existingRuleSet = findAll();
        }
        
        return existingRuleSet.get(0);
    }
    
    
}
