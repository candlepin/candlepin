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

import org.mozilla.javascript.Scriptable;

/**
 * JsonContext
 *
 * A javascript context which provides each of its context args as
 * a single JSON string. It also provides the ability to specify
 * non-serializable objects which are passed directly to the called
 * JS function.
 */
public class JsonJsContext extends JsContext {

    private final RulesObjectMapper rulesObjectMapper;
    private ArgumentJsContext nonSerializableContext;

    public JsonJsContext(RulesObjectMapper rulesObjectMapper) {
        this.rulesObjectMapper = rulesObjectMapper;
        this.nonSerializableContext = new ArgumentJsContext();
    }

    @Override
    public void applyTo(Scriptable scope) {
        scope.put("json_context", scope, this.rulesObjectMapper.toJsonString(contextArgs));
        nonSerializableContext.applyTo(scope);
    }

    public void put(String contextKey, Object contextVal, boolean serializable) {
        if (!serializable) {
            nonSerializableContext.put(contextKey, contextVal);
        }
        else {
            this.put(contextKey, contextVal);
        }
    }
}
