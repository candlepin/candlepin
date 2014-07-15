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
package org.candlepin.common.util;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;

/**
 * Basic utilities.
 */
public class Util {
    private static Logger log = LoggerFactory.getLogger(Util.class);

    private Util() {
        // This class provides only static methods
    }

    public static String toBase64(byte [] data) {
        try {
            // to be thread-safe, we should create it from the static method
            // If we don't specify the line separator, it will use CRLF
            return new String(new Base64(64, "\n".getBytes()).encode(data), "ASCII");
        }
        catch (UnsupportedEncodingException e) {
            log.warn("Unable to convert binary data to string", e);
            return new String(data);
        }
    }
}
