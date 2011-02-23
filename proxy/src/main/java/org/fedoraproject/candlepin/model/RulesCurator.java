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


import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.hibernate.Criteria;

import com.wideplay.warp.persist.Transactional;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

/**
 * RulesCurator
 */
public class RulesCurator extends AbstractHibernateCurator<Rules> {
    private static Logger log = Logger.getLogger(RulesCurator.class);

    protected RulesCurator() {
        super(Rules.class);
    }
    
    /**
     * updates the rules to the given values.
     * @param updatedRules latest rules
     * @return a copy of the latest rules
     */
    @Transactional
    public Rules update(Rules updatedRules) {
        List<Rules> existingRuleSet = listAll();
        if (existingRuleSet.size() == 0) {
            return create(updatedRules);
        }
        for (Rules rule : existingRuleSet) {
            delete(rule);
        }
        create(updatedRules);
        return updatedRules;
    }
    
    /**
     * @return the rules
     */
    public Rules getRules() {
        List<Rules> existingRuleSet = listAll();
        if (existingRuleSet.size() == 0) {
            return rulesFromFile(getDefaultRulesFile());
        }
        return existingRuleSet.get(0);
    }
    
    /**
     * Get the last updated timestamp for the rules (either from disk or db),
     * without reading in the full rules file.
     * 
     * @return the last updated timestamp for the rules
     */
    public Date getUpdated() {
        List<Rules> dbRules = listAll();
        if (dbRules.size() > 0) {
            return dbRules.get(0).getUpdated();
        }
        
        URL rulesUrl = this.getClass().getResource(getDefaultRulesFile());
        File rulesFile;
        try {
            rulesFile = new File(rulesUrl.toURI());
        }
        catch (URISyntaxException e) {
            throw new CuratorException(e);
        }
        return new Date(rulesFile.lastModified());
    }
    
    @AllowRoles(roles = Role.SUPER_ADMIN)
    public Rules create(Rules entity) {
        return super.create(entity);
    }
    
    @AllowRoles(roles = Role.SUPER_ADMIN)
    public void delete(Rules entity) {
        super.delete(entity);
    }
    
    @AllowRoles(roles = Role.SUPER_ADMIN)
    public Rules merge(Rules entity) {
        return super.merge(entity);
    }

    private Rules rulesFromFile(String path) {
        InputStream is = this.getClass().getResourceAsStream(path);
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader reader = new BufferedReader(isr);
        StringBuilder builder = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line + "\n");
            }
        }
        catch (IOException e) {
            throw new CuratorException(e);
        }
        finally {
            try {
                reader.close();
            }
            catch (IOException e) {
                log.warn("problem closing reader for " + path, e);
            }
        }
        return new Rules(builder.toString());
    }
    
    protected String getDefaultRulesFile() {
        return "/rules/default-rules.js";
    }
}
