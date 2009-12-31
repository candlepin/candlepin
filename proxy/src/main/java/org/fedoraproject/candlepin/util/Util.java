package org.fedoraproject.candlepin.util;

import java.util.UUID;

/**
 * Genuinely random utilities.
 */
public class Util {

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

}
