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
package org.candlepin.policy.js.override;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.InputStream;
import java.util.Date;

/**
 * OverrideRulesTests
 */
@RunWith(MockitoJUnitRunner.class)
public class OverrideRulesTests {

    @Mock
    private RulesCurator rulesCuratorMock;
    private JsRunnerProvider provider;
    private OverrideRules overrideRules;
    @Mock
    private Configuration config;

    @Before
    public void setupTest() {
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);

        provider = new JsRunnerProvider(rulesCuratorMock);
        overrideRules = new OverrideRules(provider.get(), config);
    }

    @Test
    public void canOverrideNonBlacklistedProperty() {
        assertTrue("gpgcheck should not be black listed.",
            this.overrideRules.canOverrideForConsumer("gpgcheck"));
    }

    @Test
    public void testNameIsBlacklisted() {
        this.checkBlackList("name");
    }

    @Test
    public void testBaseurlIsBlackListedHosted() {
        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        this.checkBlackList("baseurl");
    }

    @Test
    public void canOverrideBaseurlOnStandalone() {
        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(true);
        assertTrue("baseurl should not be black listed.",
            this.overrideRules.canOverrideForConsumer("baseurl"));
    }


    @Test
    public void testLabelIsBlackListed() {
        this.checkBlackList("label");
    }

    void checkBlackList(String overrideName) {
        assertFalse(overrideName + " should be blacklisted.",
            this.overrideRules.canOverrideForConsumer(overrideName));
    }
}
