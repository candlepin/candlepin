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
import java.security.SecureRandom;



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

    public static long generateUniqueLong() {
        /*
          This deserves explanation.

          A random positive Long has 63 bits of hash space.  We want
          to have a given amount of certainty about the probability of
          collisions within this space.  This is an instance of the
          Birthday Problem[1].  We can get the probability that any
          two random numbers collide with the approximation:

          1-e**((-(N**2))/(2H))

          Where e is Euler's number, N is the number of random numbers
          generated, and H is the number of possible random outcomes.

          Suppose then that we generated one billion serials, with
          each serial being a 63-bit positive Long.  Then our
          probability of having a collision would be:

          irb(main):001:0> 1-Math.exp((-(1000000000.0**2))/(2.0*(2**63)))
          => 0.052766936243662

          So, if we generated a *billion* such serials, there is only
          a 5% chance that any two of them would be the same.  In
          other words, there is 95% chance that we would not have a
          single collision in one billion entries.

          The chances obviously get even less likely with smaller
          numbers.  With one million, the probability of a collision
          is:

          irb(main):002:0> 1-Math.exp((-(1000000.0**2))/(2.0*(2**63)))
          => 5.42101071809853e-08

          Or, 1 in 18,446,744.

          [1] http://en.wikipedia.org/wiki/Birthday_problem
         */

        return Math.abs(new SecureRandom().nextLong());
    }
}
