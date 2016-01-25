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
package org.candlepin.util;

import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.pki.PKIReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509CRLEntryWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.jce.provider.X509CRLEntryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509CRL;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * CrlFileUtil
 */
@Singleton
public class CrlFileUtil {
    private static final Logger log = LoggerFactory.getLogger(CrlFileUtil.class);

    private static final Pattern CRL_HEADER_PATTERN = Pattern.compile("^(-+)BEGIN (.+)\\1$");
    private static final Pattern CRL_FOOTER_PATTERN = Pattern.compile("^(-+)END (.+)\\1$");
    private static final Pattern WHITESPACE = Pattern.compile("^\\s.*$");

    private final PKIReader pkiReader;
    private final PKIUtility pkiUtility;
    private CertificateSerialCurator certificateSerialCurator;

    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

    @Inject
    public CrlFileUtil(PKIReader pkiReader, PKIUtility pkiUtility, CertificateSerialCurator curator) {
        this.pkiReader = pkiReader;
        this.pkiUtility = pkiUtility;
        this.certificateSerialCurator = curator;
    }

    /**
     * Initializes a new CRL at the specified location
     *
     * @param file
     *  The file to initialize
     *
     * @throws IOException
     *  If an IO error occurs while initializing the CRL file
     */
    public void initializeCRLFile(File file, Collection<BigInteger> revoke) throws IOException {
        FileOutputStream output = null;

        List<X509CRLEntryWrapper> entries = new LinkedList<X509CRLEntryWrapper>();

        for (BigInteger serial : revoke) {
            entries.add(new X509CRLEntryWrapper(serial, new Date()));
        }

        X509CRL crl = this.pkiUtility.createX509CRL(entries, BigInteger.ONE);

        try {
            output = new FileOutputStream(file);
            this.pkiUtility.writePemEncoded(crl, output);
        }
        finally {
            IOUtils.closeQuietly(output);
        }
    }

    /**
     * Opens the specified CRL file for streaming. If the CRL file contains a header in the form of
     * "/-+BEGIN .+-+\n/i", the header and matching footer will be automatically truncated.
     *
     * Note: The stream returned by this method must be closed when the calling function is done
     * with it.
     *
     * @param file
     *  The file to open as a CRL file.
     *
     * @throws IOException
     *  If an IO error occurs while open the CRL file
     *
     * @return
     *  an input stream for the specified CRL file
     */
    public File stripCRLFile(File file) throws IOException {
        File tempFile = File.createTempFile("candlepin_crl_", ".pem");
        BufferedReader r = new BufferedReader(new FileReader(file));
        BufferedWriter w = new BufferedWriter(new FileWriter(tempFile));

        String line = null;

        try {
            do {
                line = r.readLine();
            }
            while (line != null &&
                (WHITESPACE.matcher(line).matches() || CRL_HEADER_PATTERN.matcher(line).matches())
            );

            if (line == null) {
                throw new IOException("No data left in file " + file);
            }

            do {
                w.write(line);
                line = r.readLine();
            }
            while (line != null && !CRL_FOOTER_PATTERN.matcher(line).matches());
            return tempFile;
        }
        finally {
            w.close();
            r.close();
        }
    }

    /**
     * Updates the specified CRL file by adding or removing entries. If both lists are either null
     * or empty, the CRL file will not be modified by this method. If the file does not exist or
     * appears to be empty, it will be initialized before processing the lists.
     *
     * @param file
     *  The CRL file to update
     *
     * @param revoke
     *  A collection of serials to revoke (add)
     *
     * @param unrevoke
     *  A collection of serials to unrevoke (remove)
     *
     * @throws IOException
     *  if an IO error occurs while updating the CRL file
     */
    public void updateCRLFile(File file, final Collection<BigInteger> revoke,
        final Collection<BigInteger> unrevoke) throws IOException {

        if (!file.exists() || file.length() == 0) {
            this.initializeCRLFile(file, revoke);
            return;
        }

        File strippedFile = stripCRLFile(file);

        InputStream input = null;
        InputStream reaper = null;

        BufferedOutputStream output = null;
        OutputStream filter = null;
        OutputStream encoder = null;

        try {
            // Impl note:
            // Due to the way the X509CRLStreamWriter works (and the DER format in general), we have
            // to make two passes through the file.
            input = new Base64InputStream(new FileInputStream(strippedFile));
            reaper = new Base64InputStream(new FileInputStream(strippedFile));

            // Note: This will break if we ever stop using RSA keys
            PrivateKey key = this.pkiReader.getCaKey();
            X509CRLStreamWriter writer = new X509CRLStreamWriter(
                input, (RSAPrivateKey) key);

            // Add new entries
            if (revoke != null) {
                Date now = new Date();
                for (BigInteger serial : revoke) {
                    writer.add(serial, now, CRLReason.privilegeWithdrawn);
                }
            }

            // Unfortunately, we need to do the prescan before checking if we have changes queued,
            // or we could miss cases where we have entries to remove, but nothing to add.
            if (unrevoke != null && !unrevoke.isEmpty()) {
                writer.preScan(reaper, new CRLEntryValidator() {
                    public boolean shouldDelete(X509CRLEntryObject entry) {
                        return unrevoke.contains(entry.getSerialNumber());
                    }
                });
            }
            else {
                writer.preScan(reaper);
            }

            // Verify we actually have work to do now
            if (writer.hasChangesQueued()) {
                output = new BufferedOutputStream(new FileOutputStream(file));
                filter = new FilterOutputStream(output) {
                    private boolean needsLineBreak = true;

                    public void write(int b) throws IOException {
                        this.needsLineBreak = (b != (byte) '\n');
                        super.write(b);
                    }

                    public void write(byte[] buffer) throws IOException {
                        this.needsLineBreak = (buffer[buffer.length - 1] != (byte) '\n');
                        super.write(buffer);
                    }

                    public void write(byte[] buffer, int off, int len) throws IOException {
                        this.needsLineBreak = (buffer[off + len - 1] != (byte) '\n');
                        super.write(buffer, off, len);
                    }

                    public void close() throws IOException {
                        if (this.needsLineBreak) {
                            super.write((int) '\n');
                            this.needsLineBreak = false;
                        }

                        // Impl note:
                        // We're intentionally not propagating the call here.
                    }
                };
                encoder = new Base64OutputStream(filter, true, 76, new byte[] { (byte) '\n' });

                output.write("-----BEGIN X509 CRL-----\n".getBytes());

                writer.lock();
                writer.write(encoder);
                encoder.close();
                filter.close();

                output.write("-----END X509 CRL-----\n".getBytes());
                output.close();
            }
        }
        catch (GeneralSecurityException e) {
            // This should never actually happen
            log.error("Unexpected security error occurred while retrieving CA key", e);
        }
        catch (CryptoException e) {
            // Something went horribly wrong with the stream writer
            log.error("Unexpected error occurred while writing new CRL file", e);
        }
        finally {
            for (Closeable stream : Arrays.asList(encoder, output, reaper, input)) {
                if (stream != null) {
                    try {
                        stream.close();
                    }
                    catch (IOException e) {
                        log.error("Unexpected exception occurred while closing stream: {}", stream, e);
                    }
                }
            }

            if (!strippedFile.delete()) {
                log.error("Unable to delete temporary CRL file: {}", strippedFile);
            }
        }
    }

    public boolean syncCRLWithDB(File file) throws IOException {
        List<BigInteger> revoke = new LinkedList<BigInteger>();
        List<CertificateSerial> serials = this.certificateSerialCurator.retrieveTobeCollectedSerials();
        for (CertificateSerial serial : serials) {
            revoke.add(serial.getSerial());
            serial.setCollected(true);
        }

        List<BigInteger> unrevoke = new LinkedList<BigInteger>();
        for (CertificateSerial serial : this.certificateSerialCurator.getExpiredSerials()) {
            unrevoke.add(serial.getSerial());
        }

        if (revoke.size() > 0 || unrevoke.size() > 0) {
            this.updateCRLFile(file, revoke, unrevoke);

            // Store the state of the newly-revoked serials as "collected"
            this.certificateSerialCurator.saveOrUpdateAll(serials);
        }

        return true;
    }
}
