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

import org.candlepin.pki.PKIUtility;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CrlFileUtil
 */
@Singleton
public class CrlFileUtil {
    private static Logger log = LoggerFactory.getLogger(CrlFileUtil.class);
    private final PKIUtility pkiUtility;
    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

    @Inject
    public CrlFileUtil(PKIUtility pkiUtility) {
        this.pkiUtility = pkiUtility;
    }

    /**
     * Given a file to CRL, this method will read it in if
     *  it's none empty and return an X.509 CRL structure.
     * @param file to the CRL
     * @return X.509 CRL structure
     * @throws CRLException thrown if there's a problem parsing the input file
     * @throws CertificateException thrown if there's a problem parsing the input file
     * @throws IOException thrown if there's general I/O problems
     */
    public X509CRL readCRLFile(File file)
        throws CRLException, CertificateException, IOException {

        FileInputStream in = null;
        lock.readLock().lock();
        try {
            if (file.exists() && file.length() > 0) {
                log.info("CRL File: {} exists. Loading the old CRL", file);
                in = new FileInputStream(file);
            }
            else {
                log.info("CRL File: {} either does not exist or is empty.", file);
            }
            X509CRL x509crl = null;
            if (in != null) {
                x509crl = (X509CRL) CertificateFactory.getInstance("X.509")
                    .generateCRL(in);
            }

            return x509crl;
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (IOException e) {
                    log.error("An exception occurred while closing a CRL file: {}", file.getAbsolutePath());
                    // we tried, we failed. better luck next time!
                }
            }
            lock.readLock().unlock();
        }
    }

    public void writeCRLFile(File file, X509CRL crl)
        throws CRLException, CertificateException, IOException {

        FileOutputStream stream = null;
        lock.writeLock().lock();
        try {
            log.info("Generating CRL and writing it to disk");

            if (file.getParentFile() != null) {
                FileUtils.forceMkdir(file.getParentFile());
            }

            stream = new FileOutputStream(file);
            pkiUtility.writePemEncoded(crl, stream);
        }
        finally {
            if (stream != null) {
                try {
                    stream.close();
                }
                catch (IOException e) {
                    log.error("An exception occurred while closing a CRL file: {}", file.getAbsolutePath());
                }
            }
            lock.writeLock().unlock();
        }
    }
}
