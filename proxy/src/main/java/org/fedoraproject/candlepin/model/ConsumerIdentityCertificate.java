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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;


/**
 * Represents certificate used to identify a consumer
 * 
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ConsumerIdentityCertificate implements Persisted {

    private Long id;
    
    private byte[] key;
//    private String pem;
    private byte[] pem;
    
    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }


    public byte[] getPem() {
        return pem;
    }


    public void setPem(byte[] pem) {
        this.pem = pem;
    }


    public Long getId() {
        // TODO Auto-generated method stub
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }

    
    public void update(ConsumerIdentityCertificate other){
        this.setKey(other.getKey());
        this.setPem(other.getPem());
    }
}
