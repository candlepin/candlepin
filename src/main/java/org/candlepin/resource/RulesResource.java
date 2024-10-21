/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.model.CuratorException;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.resource.server.v1.RulesApi;

import com.google.inject.persist.Transactional;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import javax.inject.Inject;


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
    @Transactional
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
}
