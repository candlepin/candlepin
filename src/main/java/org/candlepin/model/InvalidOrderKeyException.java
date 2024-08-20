/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import java.util.Optional;

import javax.persistence.metamodel.ManagedType;



/**
 * The InvalidOrderKeyException is thrown when an invalid ordering key is provided to a curator
 * for sorting a query result.
 */
public class InvalidOrderKeyException extends IllegalArgumentException {

    private final String column;
    private final Optional<ManagedType<?>> type;

    /**
     * Constructs a new exception indicating the specified ordering column could not be applied.
     *
     * @param column
     *  the name of the column triggering this exception; also used as the exception message
     *
     * @param type
     *  the metamodel type for the root to which the column was applied, if available
     */
    public InvalidOrderKeyException(String column, ManagedType<?> type) {
        super(column);

        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("column is null or empty");
        }

        this.column = column;
        this.type = Optional.ofNullable(type);
    }

    /**
     * Constructs a new exception indicating the specified ordering column could not be applied.
     *
     * @param column
     *  the name of the column triggering this exception; also used as the exception message
     *
     * @param type
     *  the metamodel type for the root to which the column was applied, if available
     *
     * @param cause
     *  the cause (which is saved for later retrieval by the Throwable.getCause() method). A null
     *  value is permitted, and indicates that the cause is nonexistent or unknown
     */
    public InvalidOrderKeyException(String column, ManagedType<?> type, Throwable cause) {
        super(column, cause);

        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("column is null or empty");
        }

        this.column = column;
        this.type = Optional.ofNullable(type);
    }

    /**
     * Fetches the column name that triggered this exception.
     *
     * @return
     *  the name of the column that triggered this exception
     */
    public String getColumn() {
        return this.column;
    }

    /**
     * Fetches the type of the root on which the order column was attempted to be applied.
     *
     * @return
     *  the metamodel of the entity type, if available
     */
    public Optional<ManagedType<?>> getManagedType() {
        return this.type;
    }
}
