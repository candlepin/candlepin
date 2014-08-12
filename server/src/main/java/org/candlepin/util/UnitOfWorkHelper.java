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

import com.google.inject.persist.UnitOfWork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UnitOfWorkHelper encapsulates a UnitOfWork to help us commit
 * and restart transactions.
 */
public class UnitOfWorkHelper {

    private static Logger log = LoggerFactory.getLogger(UnitOfWorkHelper.class);
    private UnitOfWork unitOfWork;

    public UnitOfWorkHelper(UnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
    }

    public boolean startUnitOfWork() {
        if (unitOfWork != null) {
            try {
                unitOfWork.begin();
                return true;
            }
            catch (IllegalStateException e) {
                log.debug("Already have an open unit of work");
                return false;
            }
        }
        return false;
    }

    public void endUnitOfWork() {
        if (unitOfWork != null) {
            try {
                unitOfWork.end();
            }
            catch (IllegalStateException e) {
                log.debug("Unit of work is already closed, doing nothing");
                // If there is no active unit of work, there is no reason to close it
            }
        }
    }

    public void commitAndContinue() {
        endUnitOfWork();
        startUnitOfWork();
    }
}
