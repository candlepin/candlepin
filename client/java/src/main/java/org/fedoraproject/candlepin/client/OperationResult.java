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
package org.fedoraproject.candlepin.client;

/**
 * The Enum OperationResult.
 */
public enum OperationResult {

    /** The INVALI d_ uuid. */
    INVALID_UUID("User id is not valid."),

    /** The ERRO r_ whil e_ savin g_ certificates. */
    ERROR_WHILE_SAVING_CERTIFICATES("Error occured when saving cert/keys to disk."),

    /** The NO t_ a_ failure. */
    NOT_A_FAILURE("Success!"),

    /** The CLIEN t_ no t_ registered. */
    CLIENT_NOT_REGISTERED("Client is not registered"),

    /** The UNKNOWN. */
    UNKNOWN("Failure reason not known.");


    /** The reason. */
    private String reason;

    /**
     * Instantiates a new operation result.
     *
     * @param reason the reason
     */
    OperationResult(String reason) {
        this.reason = reason;
    }

    /**
     * Gets the reason.
     *
     * @return the reason
     */
    public String getReason() {
        return reason;
    }

}
