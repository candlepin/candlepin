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
package org.fedoraproject.candlepin.model;

import com.wideplay.warp.persist.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * RulesCurator
 */
public class RulesCurator extends AbstractHibernateCurator<Rules> {

    protected RulesCurator() {
        super(Rules.class);
    }
    
    
    /**
     * updates the rules to the given values.
     * @param updatedRules latest rules
     * @return a copy of the latest rules
     */
    @Transactional
    // This seems lame...
    public Rules update(Rules updatedRules) {
        List<Rules> existingRuleSet = findAll();
        if (existingRuleSet.size() == 0) {
            return create(updatedRules);
        }
        for (Rules rule : existingRuleSet) {
            delete(rule);
        }
        create(updatedRules);
        return updatedRules;
        
    }
    
    private void initiateRulesFromFile() {
        String path = getDefaultRulesFile();
        InputStream is = this.getClass().getResourceAsStream(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line + "\n");
            }
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Rules rules = new Rules(builder.toString());
        create(rules);
    }
    
    protected String getDefaultRulesFile() {
        return "/rules/default-rules.js";
    }

    /**
     * @return the rules
     */
    public Rules getRules() {
        List<Rules> existingRuleSet = findAll();
        if (existingRuleSet.size() == 0) {
            initiateRulesFromFile();
            existingRuleSet = findAll();
        }
        
        return existingRuleSet.get(0);
    }
    
    
}
