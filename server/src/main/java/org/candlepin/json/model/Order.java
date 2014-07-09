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
package org.candlepin.json.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Order
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Order {

    private String number;
    private Long quantity;
    private String start;
    private String end;
    private String contract;
    private String account;

    /**
     * @param number
     */
    public void setNumber(String number) {
        this.number = number;
    }

    /**
     * @param quantity
     */
    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    /**
     * @param start
     */
    public void setStart(String start) {
        this.start = start;
    }

    /**
     * @param end
     */
    public void setEnd(String end) {
        this.end = end;
    }

    /**
     * @param contract
     */
    public void setContract(String contract) {
        this.contract = contract;
    }

    /**
     * @param accountNumber
     */
    public void setAccount(String account) {
        this.account = account;
    }
}
