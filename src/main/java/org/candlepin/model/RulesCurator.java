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

import org.candlepin.exceptions.NotFoundException;
import org.candlepin.util.Util;
import org.candlepin.util.VersionUtil;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

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
        updatedRules.setRulesSource(Rules.RulesSourceEnum.DATABASE);
        Rules result = this.create(updatedRules);
        return result;
    }

    @Override
    @Transactional
    public Rules create(Rules toCreate) {
        Rules current = getDbRules();
        if (current != null && !VersionUtil.getRulesVersionCompatibility(
                current.getVersion(), toCreate.getVersion())) {
            return current;
        }
        return super.create(toCreate);
    }

    public Rules getDbRules() {
        return (Rules) this.currentSession().createCriteria(Rules.class)
        .addOrder(Order.desc("updated"))
        .setMaxResults(1)
        .uniqueResult();
    }

    public void updateDbRules() {
        Rules dbRules = getDbRules();

        // Load rules from RPM, we need to know it's version before we know which
        // rules to use:
        Rules rpmRules = rulesFromFile(getDefaultRulesFile());
        log.debug("RPM Rules version: " + rpmRules.getVersion());

        if (dbRules == null ||
            !VersionUtil.getRulesVersionCompatibility(rpmRules.getVersion(),
                dbRules.getVersion())) {
            this.resetToRpmRules();
        }
    }

    /**
     * @return the rules
     */
    public Rules getRules() {
        Rules dbRules = getDbRules();
        if (dbRules == null) {
            log.error("There is no rules file in the database, something is very wrong.");
            throw new NotFoundException(i18n.tr("No rules file found in the database"));
        }
        return dbRules;
    }

    private Date getUpdatedFromDB() {
        return (Date) this.currentSession().createCriteria(Rules.class)
            .setProjection(Projections.projectionList()
                .add(Projections.max("updated")))
                .uniqueResult();
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
    public void resetToRpmRules() {
        currentSession().createQuery("DELETE FROM Rules").executeUpdate();
        this.create(rulesFromFile(DEFAULT_RULES_FILE));
    }

    @Override
    @Transactional
    public void delete(Rules toDelete) {
        this.resetToRpmRules();
    }

    private Rules rulesFromFile(String path) {
        InputStream is = this.getClass().getResourceAsStream(path);
        Rules result = new Rules(Util.readFile(is));
        result.setRulesSource(Rules.RulesSourceEnum.DEFAULT);
        return result;
    }

    protected String getDefaultRulesFile() {
        return DEFAULT_RULES_FILE;
    }
}
