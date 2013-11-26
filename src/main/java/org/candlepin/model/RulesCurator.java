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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static Logger log = LoggerFactory.getLogger(RulesCurator.class);
    public static final String DEFAULT_RULES_FILE = "/rules/rules.js";

    /*
     * Current rules API major version number. (the x in x.y) If a rules file does not
     * match this major version number exactly, we do not import the rules.
     */
    public static final int RULES_API_VERSION = 5;

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
        List<Rules> dbRuleSet = listAll();
        // Load rules from RPM, we need to know it's version before we know which
        // rules to use:
        Rules rpmRules = rulesFromFile(getDefaultRulesFile());
        rpmRules.setRulesSource(Rules.RulesSourceEnum.DEFAULT);
        log.debug("RPM Rules version: " + rpmRules.getVersion());

        // If there are rules in the database and their version is not less than the
        // version this server is currently running, we'll use them:
        if (!dbRuleSet.isEmpty() &&
            VersionUtil.getRulesVersionCompatibility(rpmRules.getVersion(),
                dbRuleSet.get(0).getVersion())) {
            log.debug("Using rules from database, version: " +
                dbRuleSet.get(0).getVersion());
            dbRuleSet.get(0).setRulesSource(Rules.RulesSourceEnum.DATABASE);
            return dbRuleSet.get(0);
        }

        if (!dbRuleSet.isEmpty()) {
            log.warn("Ignoring older rules in database, version: " +
                dbRuleSet.get(0).getVersion());
        }

        log.debug("Using default rules from RPM.");
        return rpmRules;

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

    @Transactional
    public void delete(Rules entity) {
        List<Rules> existingRuleSet = listAll();

        for (Rules rule : existingRuleSet) {
            super.delete(rule);
        }
        create(rulesFromFile(getDefaultRulesFile()));

    }

    private Rules rulesFromFile(String path) {
        InputStream is = this.getClass().getResourceAsStream(path);
        return new Rules(Util.readFile(is));
    }

    protected String getDefaultRulesFile() {
        return DEFAULT_RULES_FILE;
    }
}
