/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

package org.candlepin.pki;

import static org.junit.Assert.assertArrayEquals;

import org.candlepin.pki.impl.DefaultSubjectKeyIdentifierWriter;

import org.junit.Test;

public class SubjectKeyIdentifierWriterTest {

    @Test
    public void testToOctetString() throws Exception {
        DefaultSubjectKeyIdentifierWriter writer = new DefaultSubjectKeyIdentifierWriter();

        // 0x04 is the DER tag for an octet string and 0x06 is the subsequent length of the octet string
        byte[] foobarInAsn1 = {0x04, 0x06, 0x66, 0x6f, 0x6f, 0x62, 0x61, 0x72};
        assertArrayEquals(foobarInAsn1, writer.toOctetString("foobar".getBytes("UTF-8")));
    }
}
