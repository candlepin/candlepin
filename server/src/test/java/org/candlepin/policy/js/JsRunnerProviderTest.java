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
package org.candlepin.policy.js;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.model.Rules;
import org.candlepin.model.Rules.RulesSourceEnum;
import org.candlepin.model.RulesCurator;

import com.google.inject.Provider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;
/**
 * JsRunnerProviderTest
 */
@RunWith(MockitoJUnitRunner.class)
public class JsRunnerProviderTest {
    //@Mock private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock private JsRunnerRequestCacheFactory cacheProvider;
    @Mock private RulesCurator rulesCurator;
    @Mock
    private JsRunnerRequestCache mockedCache;
    @Mock private Rules rules;
    private Date time1;
    //private JsRunnerProvider provider;
    private JsRunnerFactory provider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        time1 = new Date();
        when(rulesCurator.getUpdated()).thenReturn(time1);
        when(rulesCurator.getRules()).thenReturn(rules);
        when(rules.getRules()).thenReturn("");
        when(rules.getRulesSource()).thenReturn(RulesSourceEnum.DATABASE);
        //provider = new JsRunnerProvider(rulesCurator, cacheProvider);
        provider = new JsRunnerFactory(rulesCurator, cacheProvider);
    }

    @Test
    public void valueGetsCachedMockedTest() {
        when(cacheProvider.getObject()).thenReturn(mockedCache);
        when(mockedCache.getUpdated()).thenReturn(null).thenReturn(time1);
        provider.getObject();
        verify(mockedCache).getUpdated();
        verify(mockedCache).setUpdated(time1);
        provider.getObject();
        verify(mockedCache, times(2)).getUpdated();
        verifyNoMoreInteractions(mockedCache);
    }

    /**
     * The rules curator is hit once during the initialization, but
     * then subsequent calls to provider.get() will not hit it anymore
     */
    @Test
    public void rulesCuratorIsNotHitMultipleTimes() {
        when(cacheProvider.getObject()).thenReturn(new JsRunnerRequestCache());
        provider.getObject();
        provider.getObject();
        provider.getObject();
        provider.getObject();
        provider.getObject();
        verify(rulesCurator, times(2)).getUpdated();
    }

    @Test
    public void cacheInvalidationOnNewRequest() {
        when(cacheProvider.getObject()).thenReturn(new JsRunnerRequestCache());
        provider.getObject();
        provider.getObject();
        provider.getObject();
        JsRunnerRequestCache newCache = new JsRunnerRequestCache();
        when(cacheProvider.getObject()).thenReturn(newCache);
        Assert.assertEquals(null, newCache.getUpdated());
        provider.getObject();
        Assert.assertEquals(time1, newCache.getUpdated());
        provider.getObject();
        verify(rulesCurator, times(3)).getUpdated();
    }

}
