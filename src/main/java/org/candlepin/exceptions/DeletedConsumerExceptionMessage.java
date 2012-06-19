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
package org.candlepin.exceptions;

/**
 * DeletedConsumerExceptionMessage
 */
public class DeletedConsumerExceptionMessage extends ExceptionMessage {
    private String deletedId;


    public DeletedConsumerExceptionMessage(String message, String deletedId) {
        super(message);
        this.deletedId = deletedId;
    }

    public String getDeletedId() {
        return deletedId;
    }

    public void setDeletedId(String deletedId) {
        this.deletedId = deletedId;
    }


}
