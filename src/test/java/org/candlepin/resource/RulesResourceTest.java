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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;


/**
 * RulesResourceTest
 */
public class RulesResourceTest extends DatabaseTestFixture {
    private RulesCurator rulesCurator;
    private RulesResource rulesResource;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();
        rulesCurator = injector.getInstance(RulesCurator.class);
        rulesResource = injector.getInstance(RulesResource.class);
    }

    @Test
    public void testGet() {
        String rulesBuffer = TestUtil.createRulesBlob(10000);
        Rules rules = new Rules(rulesBuffer);
        rules.setRulesSource(Rules.RulesSourceEnum.DATABASE);
        rulesCurator.create(rules);
        String rulesBlob = rulesResource.getRules();
        assertEquals(new String(Base64.getDecoder().decode(rulesBlob)), rulesBuffer);
    }
}
