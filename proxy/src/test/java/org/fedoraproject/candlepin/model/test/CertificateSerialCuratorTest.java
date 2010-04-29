/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.*;

import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Test;

/**
 * CertificateSerialCuratorTest
 */
public class CertificateSerialCuratorTest extends DatabaseTestFixture {

    @Test
    public void testOldSerialsDeleted() {
        Long firstSerial = certSerialCurator.getNextSerial();
        CertificateSerial serial = certSerialCurator.find(firstSerial);
        assertEquals(firstSerial, serial.getId());

        Long secondSerial = certSerialCurator.getNextSerial();
        Long expected = firstSerial + 1;
        assertEquals(expected, secondSerial);

        Long thirdSerial = certSerialCurator.getNextSerial();
        expected = secondSerial + 1;
        assertEquals(expected, thirdSerial);

        // First two should be gone now:
        assertNull(certSerialCurator.find(firstSerial));
        assertNull(certSerialCurator.find(secondSerial));

        // Make sure we didn't delete the latest:
        assertNotNull(certSerialCurator.find(thirdSerial));
    }

}
