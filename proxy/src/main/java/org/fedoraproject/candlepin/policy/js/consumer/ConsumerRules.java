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

import java.util.HashMap;
import java.util.Map;

import com.google.inject.Inject;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.ReadOnlyConsumer;

/**
 * ConsumerRules
 */
public class ConsumerRules {

    private JsRules jsRules;
    
    @Inject
    public ConsumerRules(JsRules jsRules) {
        this.jsRules = jsRules;
        jsRules.init("consumer_delete_name_space");
    }

    public ConsumerDeleteHelper onConsumerDelete(
            ConsumerDeleteHelper consumerDeleteHelper, Consumer consumer) {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", new ReadOnlyConsumer(consumer));
        args.put("helper", consumerDeleteHelper);

        jsRules.invokeRule("global", args);
        
        return consumerDeleteHelper;
    }
}
