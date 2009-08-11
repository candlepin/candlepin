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

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlTransient;


public class ConsumerInfo {
    
    private Consumer parent;
    private ConsumerType type;
    private Map<String, String> metadata;
    
    
    
    /**
     * @return Returns the parent.
     */
    @XmlTransient
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
     * @return Returns the type.
     */
    public ConsumerType getType() {
        return type;
    }
    
    /**
     * @param typeIn The type to set.
     */
    public void setType(ConsumerType typeIn) {
        type = typeIn;
    }

    
    /**
     * @return Returns the metadata.
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    
    /**
     * @param metadataIn The metadata to set.
     */
    public void setMetadata(Map<String, String> metadataIn) {
        metadata = metadataIn;
    }
    
    /**
     * Set a metadata field
     * @param name to set
     * @param value to set
     */
    public void setMetadataField(String name, String value) {
        if (this.metadata == null) {
            metadata = new HashMap();
        }
        
    }
    
    /**
     * Get a metadata field value
     * @param name of field to fetch
     * @return String field value.
     */
    public String getMetadataField(String name) {
       if (this.metadata != null) {
           return metadata.get(name);
       }
       return null;
    }
}
