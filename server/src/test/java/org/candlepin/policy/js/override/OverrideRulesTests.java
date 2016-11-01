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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.util.Util;

import com.google.inject.Provider;

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
    @Mock
    private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock
    private JsRunnerRequestCache cache;
    @Mock private ProductCurator productCurator;
    @Before
    public void setupTest() {
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        when(cacheProvider.get()).thenReturn(cache);

        provider = new JsRunnerProvider(rulesCuratorMock, cacheProvider);
        overrideRules = new OverrideRules(provider.get(), config,
                new RulesObjectMapper(new ProductCachedSerializationModule(productCurator)));
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
