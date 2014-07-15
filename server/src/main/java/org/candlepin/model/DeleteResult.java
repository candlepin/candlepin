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
package org.candlepin.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class to hold the number of records removed via an unbind.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class DeleteResult {
    private int deletedRecords;

    public DeleteResult() {
    }

    public DeleteResult(int deletedRecords) {
        this.deletedRecords = deletedRecords;
    }

    /**
     * @return the deletedRecords
     */
    public int getDeletedRecords() {
        return deletedRecords;
    }

    /**
     * @param deletedRecords the deletedRecords to set
     */
    public void setDeletedRecords(int deletedRecords) {
        this.deletedRecords = deletedRecords;
    }
}
