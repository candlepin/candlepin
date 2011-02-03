/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.policy.js.consumer;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.guice.RulesReaderProvider;
import org.fedoraproject.candlepin.guice.ScriptEngineProvider;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.policy.js.ReadOnlyConsumer;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.RuleParseException;

import com.google.inject.Inject;

/**
 * ConsumerRules
 */
public class ConsumerRules {

    private Logger log = Logger.getLogger(ConsumerRules.class);

    private ScriptEngineProvider jsEngineProvider;
    private ScriptEngine jsEngine;
    private Object consumerDeleteNameSpace;
    private RulesReaderProvider rulesReaderProvider;
    private boolean initialized = false;
    

    @Inject
    public ConsumerRules(RulesReaderProvider rulesReaderProvider,
        ScriptEngineProvider jsEngineProvider) {

        this.jsEngineProvider = jsEngineProvider;
        this.rulesReaderProvider = rulesReaderProvider;

    }
    
    /*
     * The init method allows the expensive creation of the rules engine
     * to be deferred until it is actually needed. All non constructor
     * methods must call this before doing any work.
     */
    protected synchronized void init() {
        
        if (!initialized) {        
            jsEngine = jsEngineProvider.get();
            
            if (jsEngine == null) {
                throw new RuntimeException("No Javascript engine");
            }
            
            try {
                this.jsEngine.eval(rulesReaderProvider.get());
                consumerDeleteNameSpace = 
                    ((Invocable) this.jsEngine)
                        .invokeFunction("consumer_delete_name_space");
            }
            catch (ScriptException ex) {
                throw new RuleParseException(ex);
            }
            catch (NoSuchMethodException ex) {
                throw new RuleParseException(ex);
            }
            initialized = true;
        }
    }

    public ConsumerDeleteHelper onConsumerDelete(
            ConsumerDeleteHelper consumerDeleteHelper, Consumer consumer) {
        this.init();
        jsEngine.put("consumer", new ReadOnlyConsumer(consumer));
        jsEngine.put("helper", consumerDeleteHelper);

        invokeRule(consumerDeleteNameSpace, "consumer delete", "global");
        
        return consumerDeleteHelper;
    }

    protected void invokeRule(Object namespace, String namespaceName, String ruleName) {
        this.init();
        Invocable inv = (Invocable) jsEngine;
        try {
            inv.invokeMethod(namespace, ruleName);
            log.debug("Ran rule: " + ruleName + "in namespace: " + namespaceName);
        }
        catch (NoSuchMethodException ex) {
            log.warn("No rule found: in namespace: " + namespaceName);
        }
        catch (ScriptException ex) {
            throw new RuleExecutionException(ex);
        }
    }
}
