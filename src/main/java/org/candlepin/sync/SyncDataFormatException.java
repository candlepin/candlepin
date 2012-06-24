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

/**
 * SyncDataFormatException
 *
 * Thrown when encountering some kind of problem with the format of the data in a manifest
 * being imported.
 *
 * NOTE: exception messages passed here must be translated.
 */
public class SyncDataFormatException extends ImporterException {

    private static final long serialVersionUID = 1L;

    public SyncDataFormatException(String msg) {
        super(msg);
    }

}
