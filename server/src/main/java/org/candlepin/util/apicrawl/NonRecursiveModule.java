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
package org.candlepin.util.apicrawl;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * NonRecursingModule
 */
class NonRecursiveModule extends Module {

    private NonRecursiveBeanSerializerModifer modifier;

    NonRecursiveModule(ObjectMapper mapper) {
        super();
        modifier = new NonRecursiveBeanSerializerModifer(mapper);
    }

    @Override
    public String getModuleName() {
        return "non recursing module";
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addBeanSerializerModifier(modifier);
    }

    @Override
    public Version version() {
        return new Version(1, 0, 0, null, "org.candlepin", "");
    }

    void resetSeen() {
        modifier.resetSeen();
    }
}
