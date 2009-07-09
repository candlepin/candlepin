/**
 * Copyright (c) 2008 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model;

import java.util.Map;


public class ConsumerInfo {
    
    private Consumer parent;
    private String type;
    private Map<String, String> properties;
    
    /**
     * @return Returns the parent.
     */
    public Consumer getParent() {
        return parent;
    }

    /**
     * @param parentIn The parent to set.
     */
    public void setParent(Consumer parentIn) {
        parent = parentIn;
    }
    
    /**
     * @return Returns the properties.
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * @param propertiesIn The properties to set.
     */
    public void setProperties(Map<String, String> propertiesIn) {
        properties = propertiesIn;
    }

    /**
     * @return Returns the type.
     */
    public String getType() {
        return type;
    }
    
    /**
     * @param typeIn The type to set.
     */
    public void setType(String typeIn) {
        type = typeIn;
    }
}
