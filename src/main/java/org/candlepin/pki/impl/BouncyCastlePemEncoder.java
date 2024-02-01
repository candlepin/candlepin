/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
import org.candlepin.pki.PemEncodingException;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class BouncyCastlePemEncoder implements PemEncoder {
    private static final byte[] LINE_SEPARATOR = String.format("%n").getBytes();

    @Override
    public byte[] encodeAsBytes(X509Certificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("Cannot encode null certificate!");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(output)) {
            encode(certificate, writer);
            return output.toByteArray();
        }
        catch (IOException e) {
            throw new PemEncodingException("Failed to encode certificate as PEM!", e);
        }
    }

    @Override
    public String encodeAsString(X509Certificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("Cannot encode certificate null!");
        }
        try (StringWriter writer = new StringWriter(1024)) {
            encode(certificate, writer);
            return writer.toString();
        }
        catch (IOException e) {
            throw new PemEncodingException("Failed to encode certificate as PEM!", e);
        }
    }

    @Override
    public byte[] encodeAsBytes(PrivateKey privateKey) {
        return encode(privateKey);
    }

    @Override
    public String encodeAsString(PrivateKey privateKey) {
        return new String(encode(privateKey));
    }

    private byte[] encode(PrivateKey key) {
        if (key == null) {
            throw new IllegalArgumentException("Cannot encode null key!");
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(("-----BEGIN PRIVATE KEY-----\n").getBytes(StandardCharsets.UTF_8));

            // Write base64 encoded DER. Does not close the underlying stream.
            Base64OutputStream b64Out = new Base64OutputStream(out, true, 64, LINE_SEPARATOR);
            b64Out.write(key.getEncoded());
            b64Out.eof();
            b64Out.flush();

            out.write(("-----END PRIVATE KEY-----\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        }
        catch (IOException e) {
            throw new PemEncodingException("Could not encode key", e);
        }
    }

    private void encode(Object data, Writer output) {
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(output)) {
            pemWriter.writeObject(data);
            pemWriter.flush();
        }
        catch (IOException e) {
            throw new PemEncodingException("Failed to encode data as PEM!", e);
        }
    }

}
