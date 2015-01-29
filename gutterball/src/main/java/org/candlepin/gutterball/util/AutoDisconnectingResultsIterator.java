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

package org.candlepin.gutterball.util;

import org.hibernate.ScrollableResults;
import org.hibernate.Session;



/**
 * The AutoDisconnectingResultsIterator extends the ScrollableResultsIterator by automatically
 * closing the session upon completion or finalization of this object.
 * <p/>
 * Note: Unlike the AutoDisconnectingColumnarResultsIterator, this class will not automatically
 * evict objects from the session, as it does not peek into the object array returned by the "next"
 * method.
 */
public class AutoDisconnectingResultsIterator extends ScrollableResultsIterator {
    private Session session;

    /**
     * Creates a new AutoDisconnectingResultsIterator to iterate over the results provided by the
     * given ScrollableResults instance.
     *
     * @param session
     *  The Session to close upon completion of this iterator.
     *
     * @param results
     *  The ScrollableResults instance over which to iterate.
     */
    public AutoDisconnectingResultsIterator(Session session, ScrollableResults results) {
        super(results);

        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        this.session = session;
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();

        if (this.session.isConnected()) {
            this.session.disconnect();
        }
    }

    @Override
    public boolean hasNext() {
        boolean result = super.hasNext();

        if (!result && this.session.isConnected()) {
            this.session.disconnect();
        }

        return result;
    }

}
