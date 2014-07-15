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
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.candlepin.exceptions.ExceptionMessage;

/**
 * ConflictExceptionMessage: Used to serialize exception message plus a list of
 * import conflict keys.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ConflictExceptionMessage extends ExceptionMessage {

    private Set<Importer.Conflict> conflicts;

    public ConflictExceptionMessage(String displayMessage,
        Set<Importer.Conflict> conflicts) {
        super(displayMessage);
        this.conflicts = conflicts;
    }

    public ConflictExceptionMessage(String displayMessage,
        Importer.Conflict conflict) {
        super(displayMessage);
        this.conflicts = new HashSet<Importer.Conflict>();
        this.conflicts.add(conflict);
    }

    public ConflictExceptionMessage() {
        super("");
        this.conflicts = new HashSet<Importer.Conflict>();
    }

    public Set<Importer.Conflict> getConflicts() {
        return this.conflicts;
    }
}
