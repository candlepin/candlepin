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
package org.candlepin.util;

import org.hibernate.StaleStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.OptimisticLockException;
import javax.persistence.RollbackException;

/**
 * This utility class allows to determine what an exception thrown by Hibernate or
 * SQL driver means.
 *
 * One situation where this is useful is detection of constraint violations.
 * It's much easier to catch constraint violation (possibly caused by
 * concurrent requests) than to utilize pessimistic locking.
 *
 * @author fnguyen
 */
public class RdbmsExceptionTranslator {
    private static Logger log = LoggerFactory.getLogger(RdbmsExceptionTranslator.class);

    /**
     * Decides if the sqlException was thrown because of the update/delete statement
     * had no effect in the database
     *
     * @param sqlException the exception thrown by Hibernate (RDMBs Driver)
     * @return true if the SQL exception meets the criteria
     */
    public boolean isUpdateHadNoEffectException(RollbackException sqlException) {
        log.debug("Translating {}", sqlException);
        if (sqlException.getCause() != null &&
            sqlException.getCause() instanceof OptimisticLockException) {
            Exception e = (OptimisticLockException) sqlException.getCause();
            if (e.getCause() != null && e.getCause() instanceof StaleStateException) {
                return true;
            }
        }

        return false;
    }

}
