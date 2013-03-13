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

import java.util.HashMap;
import java.util.Map;

import org.mozilla.javascript.Scriptable;

/**
 * JsContext
 *
 * Represents the data exposed to a JS function. A context is applied
 * to a JS {@link Scriptable} to make context variables available when
 * a script is run.
 */
public abstract class JsContext {

    protected Map<String, Object> contextArgs;

    public JsContext() {
        this.contextArgs = new HashMap<String, Object>();
    }

    public void put(String contextKey, Object contextVal) {
        this.contextArgs.put(contextKey, contextVal);
    }

    /**
     * Apply this context's arguments to the specified {@link Scriptable}.
     *
     * @param scope the {@link Scriptable} to inject the arguments into.
     */
    public abstract void applyTo(Scriptable scope);
}
