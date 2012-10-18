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
package org.candlepin.model;

import org.candlepin.util.Util;
import org.candlepin.util.VersionUtil;

import com.google.inject.persist.Transactional;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.List;

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
     *
     * @param updatedRules latest rules
     * @return a copy of the latest rules
     */
    @Transactional
    public Rules update(Rules updatedRules) {
        List<Rules> existingRuleSet = listAll();
        if (existingRuleSet.isEmpty()) {
            return create(updatedRules);
        }
        for (Rules rule : existingRuleSet) {
            super.delete(rule);
        }
        create(updatedRules);
        return updatedRules;
    }

    /**
     * @return the rules
     */
    public Rules getRules() {
        List<Rules> existingRuleSet = listAll();
        // If there are rules in the database and their version is not less than the
        // candlepin version this server is currently running, we'll use them:
        if (!existingRuleSet.isEmpty() &&
            VersionUtil.getRulesVersionCompatibility(
                existingRuleSet.get(0).getCandlepinVersion())) {
            return existingRuleSet.get(0);
        }

        if (!existingRuleSet.isEmpty()) {
            log.warn("Ignoring older rules in database, candlepin version: " +
                existingRuleSet.get(0).getCandlepinVersion());
        }

        log.info("Loading default rules.");
        return rulesFromFile(getDefaultRulesFile());

    }

    private Date getUpdatedFromDB() {
        @SuppressWarnings("unchecked")
        List<Date> result = getEntityManager().createQuery("SELECT updated FROM Rules")
            .getResultList();
        if (result.size() < 1) {
            return null;
        }
        else {
            return result.get(0);
        }
    }

    /**
     * Get the last updated timestamp for the rules (either from disk or db),
     * without reading in the full rules file.
     *
     * @return the last updated timestamp for the rules
     */
    public Date getUpdated() {
        Date updated = getUpdatedFromDB();
        if (updated != null) {
            return updated;
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

    // @Override
    @Transactional
    public void delete(Rules entity) {
        List<Rules> existingRuleSet = listAll();

        for (Rules rule : existingRuleSet) {
            super.delete(rule);
        }
        create(rulesFromFile(getDefaultRulesFile()));

    }

    @Override
    public Rules merge(Rules entity) {
        return super.merge(entity);
    }

    private Rules rulesFromFile(String path) {
        InputStream is = this.getClass().getResourceAsStream(path);
        return new Rules(Util.readFile(is), VersionUtil.getVersionString());
    }

    protected String getDefaultRulesFile() {
        return "/rules/default-rules.js";
    }
}
