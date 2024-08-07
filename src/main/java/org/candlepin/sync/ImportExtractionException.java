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

import java.util.Map;

/**
 * ImportExtractionException
 */
public class ImportExtractionException extends ImporterException {

    public ImportExtractionException(String msg) {
        super(msg);
    }

    /**
     * @param msg exception message
     * @param e the inner exception
     */
    public ImportExtractionException(String msg, Throwable e) {
        super(msg, e);
    }

    public ImportExtractionException(String msg, Throwable e, Map<String, Object> collectedData) {
        super(msg, e, collectedData);
    }

    private static final long serialVersionUID = -4004706290899144021L;

}
