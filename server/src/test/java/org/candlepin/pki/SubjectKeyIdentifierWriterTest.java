package org.candlepin.pki;

import org.candlepin.pki.impl.DefaultSubjectKeyIdentifierWriter;
import org.hsqldb.types.Charset;
import org.junit.Test;
import static org.junit.Assert.*;

public class SubjectKeyIdentifierWriterTest {

    @Test
    public void testToOctetString() throws Exception{
        DefaultSubjectKeyIdentifierWriter writer = new DefaultSubjectKeyIdentifierWriter();

        byte[] foobar_in_asn1 = {0x4, 0x6, 0x66, 0x6f, 0x6f, 0x62, 0x61, 0x72};
        assertArrayEquals(foobar_in_asn1, writer.toOctetString("foobar".getBytes("UTF-8")));
    }
}
