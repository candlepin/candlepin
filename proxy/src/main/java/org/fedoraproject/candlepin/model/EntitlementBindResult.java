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

package org.fedoraproject.candlepin.model;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
@XmlRootElement(name = "entitlement_bind_result")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EntitlementBindResult implements Persisted {

    private long id;
    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean result) {
        this.result = result;
    }

    public void setId(long id) {
        this.id = id;
    }

    private Boolean result;
    
    /**
     * default ctor
     */
    public EntitlementBindResult() {
      
    }
    
    public EntitlementBindResult(Boolean result) {
        this.result = result;
        
    }
    
    @Override
    public Serializable getId() {
        // TODO Auto-generated method stub
        return id;
    }

}
