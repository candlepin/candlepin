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
package org.candlepin.pki.impl;

import org.candlepin.pki.PemEncoder;

import com.google.inject.Inject;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;

public class BouncyCastlePemEncoder implements PemEncoder {
    @Inject
    public BouncyCastlePemEncoder() {
    }

    @Override
    public byte[] encodeAsBytes(Object data) {
        if (data == null) {
            throw new IllegalArgumentException("Cannot encode null!");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(output)) {
            encode(data, writer);
            return output.toByteArray();
        }
        catch (IOException e) {
            throw new PemEncodingException("Failed to encode data as PEM!", e);
        }
    }

    @Override
    public String encodeAsString(Object data) {
        if (data == null) {
            throw new IllegalArgumentException("Cannot encode null!");
        }
        try (StringWriter writer = new StringWriter(1024)) {
            encode(data, writer);
            return writer.toString();
        }
        catch (IOException e) {
            throw new PemEncodingException("Failed to encode data as PEM!", e);
        }
    }

    private void encode(Object obj, Writer output) {
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(output)) {
            pemWriter.writeObject(obj);
            pemWriter.flush();
        }
        catch (IOException e) {
            throw new PemEncodingException("Failed to encode data as PEM!", e);
        }
    }

}
