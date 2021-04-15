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
package org.candlepin.resource;

import org.candlepin.audit.EventSink;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ServiceUnavailableException;
import org.candlepin.model.CuratorException;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;

import com.google.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;


/**
 * Rules API entry path
 */
public class RulesResource implements RulesApi {
    private static Logger log = LoggerFactory.getLogger(RulesResource.class);
    private RulesCurator rulesCurator;
    private I18n i18n;
    private EventSink sink;
    private JsRunnerProvider jsProvider;

    /**
     * Default ctor
     * @param rulesCurator Curator used to interact with Rules.
     */
    @Inject
    public RulesResource(RulesCurator rulesCurator,
        I18n i18n, EventSink sink, JsRunnerProvider jsProvider) {
        this.rulesCurator = rulesCurator;
        this.i18n = i18n;
        this.sink = sink;
        this.jsProvider = jsProvider;
    }

    @Override
    public String uploadRules(String rulesBuffer) {
        if (rulesBuffer == null || rulesBuffer.isEmpty()) {
            log.error("Rules file is empty");
            throw new BadRequestException(i18n.tr("Rules file is empty"));
        }

        Rules rules = null;

        try {
            String decoded = new String(Base64.decodeBase64(rulesBuffer));
            rules = new Rules(decoded);
        }
        catch (Throwable t) {
            log.error("Exception in rules upload", t);
            throw new BadRequestException(
                i18n.tr("Error decoding the rules. The text should be base 64 encoded"));
        }

        Rules oldRules = rulesCurator.getRules();
        rulesCurator.update(rules);
        sink.emitRulesModified(oldRules, rules);

        // Trigger a recompile of the JS rules so version/source are set correctly:
        jsProvider.compileRules(true);

        return rulesBuffer;
    }

    @Override
    public String getRules() {
        try {
            String rules = rulesCurator.getRules().getRules();
            if ((rules != null) && (rules.length() > 0)) {
                return Base64.encodeBase64String(rules.getBytes());
            }
            return "";
        }
        catch (CuratorException e) {
            log.error("couldn't read rules file", e);
            throw new ServiceUnavailableException(i18n.tr("couldn''t read rules file"));
        }
    }

    @Override
    public void deleteRules() {
        Rules deleteRules = rulesCurator.getRules();
        rulesCurator.delete(deleteRules);
        log.warn("Deleting rules version: " + deleteRules.getVersion());

        sink.emitRulesDeleted(deleteRules);

        // Trigger a recompile of the JS rules so version/source are set correctly:
        jsProvider.compileRules(true);
    }
}
