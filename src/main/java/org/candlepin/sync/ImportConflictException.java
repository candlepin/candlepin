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
package org.candlepin.sync;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.Response.Status;

import org.candlepin.exceptions.CandlepinException;

/**
 * ImportConflictException: An exception thrown when we encounter import conflicts
 * which can be explicitly overridden. (but weren't)
 *
 * We generally try to return all conflicts that occurred so the caller can re-try and
 * override them all if desired.
 */
public class ImportConflictException extends CandlepinException {

    public ImportConflictException(String message, Importer.Conflict type) {
        super(Status.CONFLICT, new ConflictExceptionMessage(
            message, type));
    }

    /**
     * Constructor for merging multiple import conflict exceptions into one. (so we can report
     * them all)
     *
     * @param conflictExceptions All conflict exceptions that have occurred.
     */
    public ImportConflictException(List<ImportConflictException> conflictExceptions) {
        super(Status.CONFLICT, buildExceptionMessage(conflictExceptions));

    }

    /**
     * Merge another ImportConflictException's data into this one so we can notify the
     * caller of everything that conflicted at once.
     *
     * @param e ImportConflictException thrown by a nested call.
     */
    private static ConflictExceptionMessage buildExceptionMessage(
        List<ImportConflictException> conflictExceptions) {

        StringBuffer newMessage = new StringBuffer();
        Set<Importer.Conflict> conflicts = new HashSet<Importer.Conflict>();
        for (ImportConflictException e : conflictExceptions) {
            if (newMessage.length() > 0) {
                newMessage.append("\n");
            }
            newMessage.append(e.message().getDisplayMessage());

            conflicts.addAll(e.message().getConflicts());
        }
        return new ConflictExceptionMessage(newMessage.toString(), conflicts);
    }

    @Override // just casting to return the correct sub-class
    public ConflictExceptionMessage message() {
        return (ConflictExceptionMessage) message;
    }

}
