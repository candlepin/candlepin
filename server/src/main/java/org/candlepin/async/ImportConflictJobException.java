/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.async;

import org.candlepin.sync.ImportConflictException;
import org.candlepin.sync.Importer;

import java.util.Iterator;

/**
 * The equivalent of {@link ImportConflictException}, but for asynchronous imports.
 * It is used by transforming an {@link org.candlepin.common.exceptions.CandlepinException} to a
 * {@link JobExecutionException}, fit for propagating to the job management system, without keeping the
 * redundant fields the former has (such as requestUuid & REST return code), while retaining the useful
 * information (list of conflicts, display message) accessible through its toString method.
 */
public class ImportConflictJobException extends JobExecutionException {

    private String message;

    public ImportConflictJobException(ImportConflictException importConflictException) {
        super(importConflictException.getLocalizedMessage(), true);

        StringBuilder str = new StringBuilder(importConflictException.getLocalizedMessage());
        str.append(" The following conflicts were found: [ ");
        Iterator<Importer.Conflict> conflicts = importConflictException.message().getConflicts().iterator();
        while (conflicts.hasNext()) {
            str.append(conflicts.next());
            if (conflicts.hasNext()) {
                str.append(", ");
            }
        }
        str.append(" ]");
        this.message = str.toString();
    }

    @Override
    public String toString() {
        return message;
    }
}
