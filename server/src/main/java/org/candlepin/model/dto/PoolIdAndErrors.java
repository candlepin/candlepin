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
package org.candlepin.model.dto;

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Pool
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PoolIdAndErrors implements Serializable {

    private String poolId;
    private List<String> errors;

    public PoolIdAndErrors() {
    }

    public PoolIdAndErrors(String id, List<String> errors) {
        this.poolId = id;
        this.errors = errors;
    }

    public String getPoolId() {
        return poolId;
    }
    public void setPoolId(String id) {
        this.poolId = id;
    }
    public List<String> getErrors() {
        return errors;
    }
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

}
