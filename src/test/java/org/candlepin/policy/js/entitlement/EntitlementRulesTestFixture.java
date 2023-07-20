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
package org.candlepin.policy.js.entitlement;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.test.TestUtil;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.Util;

import com.google.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18nFactory;

import java.io.InputStream;
import java.util.Locale;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EntitlementRulesTestFixture {

    @Mock
    protected RulesCurator rulesCurator;
    @Mock
    protected ConsumerCurator consumerCurator;
    @Mock
    protected ConsumerTypeCurator consumerTypeCurator;
    @Mock
    protected ComplianceStatus compliance;
    @Mock
    protected PoolManager poolManager;
    @Mock
    protected PoolService poolService;
    @Mock
    protected EntitlementCurator entitlementCurator;
    @Mock
    private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock
    private JsRunnerRequestCache cache;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    protected EnvironmentCurator environmentCurator;

    protected Enforcer enforcer;
    protected DevConfig config;
    protected Owner owner;
    protected ConsumerType consumerType;
    protected Consumer consumer;
    protected String productId = "a-product";
    protected ModelTranslator translator;

    @BeforeEach
    public void createEnforcer() {
        this.config = TestConfig.defaults();
        this.config.setProperty(ConfigProperties.PRODUCT_CACHE_MAX, "100");

        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(TestUtil.createDate(2010, 1, 1));
        when(cacheProvider.get()).thenReturn(cache);

        JsRunner jsRules = new JsRunnerProvider(rulesCurator, cacheProvider).get();

        poolService = mock(PoolService.class);

        translator = new StandardTranslator(consumerTypeCurator, environmentCurator, ownerCurator);
        enforcer = new EntitlementRules(
            new DateSourceImpl(),
            jsRules,
            I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
            config,
            consumerCurator,
            consumerTypeCurator,
            ObjectMapperFactory.getRulesObjectMapper(),
            translator,
            poolService);

        owner = TestUtil.createOwner();

        consumerType = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));
        consumer = new Consumer()
            .setName("test consumer")
            .setUsername("test user")
            .setOwner(owner)
            .setType(consumerType);
    }

    protected ConsumerType mockConsumerType(ConsumerType ctype) {
        if (ctype != null) {
            // Ensure the type has an ID
            if (ctype.getId() == null) {
                ctype.setId("test-ctype-" + ctype.getLabel() + "-" + TestUtil.randomInt());
            }

            when(consumerTypeCurator.getByLabel(ctype.getLabel())).thenReturn(ctype);
            when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()), anyBoolean())).thenReturn(ctype);
            when(consumerTypeCurator.get(ctype.getId())).thenReturn(ctype);

            doAnswer(new Answer<ConsumerType>() {
                @Override
                public ConsumerType answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    Consumer consumer = (Consumer) args[0];
                    ConsumerTypeCurator curator = (ConsumerTypeCurator) invocation.getMock();
                    ConsumerType ctype = null;

                    if (consumer == null || consumer.getTypeId() == null) {
                        throw new IllegalArgumentException("consumer is null or lacks a type ID");
                    }

                    ctype = curator.get(consumer.getTypeId());
                    if (ctype == null) {
                        throw new IllegalStateException("No such consumer type: " + consumer.getTypeId());
                    }

                    return ctype;
                }
            }).when(consumerTypeCurator).getConsumerType(any(Consumer.class));
        }

        return ctype;
    }

    protected Pool createPool(Owner owner, Product product) {
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());
        return pool;
    }

}
