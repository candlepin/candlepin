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
package org.fedoraproject.candlepin.policy.js;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Interface to the compiled Javascript rules.
 */
public class RuleManager {

    private ScriptEngine jsEngine;

    public RuleManager(String rulesPath) {
        ScriptEngineManager mgr = new ScriptEngineManager();
        jsEngine = mgr.getEngineByName("JavaScript");
        jsEngine.put("testvar", "meow");
        InputStream is = this.getClass().getResourceAsStream(rulesPath);
        try {
            Reader reader = new InputStreamReader(is);
            jsEngine.eval(reader);
        }
        catch (ScriptException ex) {
            ex.printStackTrace();
        }
    }

    public boolean validateProduct(String productLabel) {
        Invocable inv = (Invocable)jsEngine;
        try {
            inv.invokeFunction(productLabel);
        }
        catch (NoSuchMethodException e) {
            // No method for this product, assume this is not unexpected, many products
            // may want to just be a simple quantity check, which is implied and done
            // before we begin checking rules.
        }
        catch (ScriptException e) {
            e.printStackTrace();
        }
        return true;

    }
}
