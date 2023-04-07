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
package org.candlepin.sync;

import java.util.HashMap;
import java.util.Map;


/**
 * ImporterException
 */
public class ImporterException extends SyncException {

    private static final long serialVersionUID = -9086462704164995593L;

    /**
     * The data that was collected from an import before this exception was thrown.
     */
    private Map<String, Object> collectedData = new HashMap<>();

    public ImporterException(String msg) {
        super(msg);
    }

    public ImporterException(String msg, Throwable e) {
        super(msg, e);
    }

    public ImporterException(String msg, Throwable e, Map<String, Object> collectedData) {
        super(msg, e);
        this.collectedData = collectedData;
    }

    public Map<String, Object> getCollectedData() {
        return this.collectedData;
    }

}
