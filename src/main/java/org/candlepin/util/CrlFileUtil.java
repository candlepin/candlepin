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

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;

/**
 * CrlFileUtil
 */
public class CrlFileUtil {
    private static Logger log = LoggerFactory.getLogger(CrlFileUtil.class);
    private PKIUtility pkiUtility;

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
        try {
            if (file.exists() && file.length() > 0) {
                log.info("CRL File: " + file + " exists. Loading the old CRL");
                in = new FileInputStream(file);
            }
            else {
                log.info("CRL File: " + file + " either does not exist or is empty.");
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
                    log.error(
                        "exception when closing a CRL file: " + file.getAbsolutePath());
                    // we tried, we failed. better luck next time!
                }
            }
        }
    }

    public byte[] writeCRLFile(File file, X509CRL crl)
        throws CRLException, CertificateException, IOException {

        byte[] encoded = pkiUtility.getPemEncoded(crl);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        try {
            stream.write(encoded);
            log.info("Completed generating CRL. Writing it to disk");
            FileUtils.writeByteArrayToFile(file, stream.toByteArray());
        }
        finally {
            if (stream != null) {
                try {
                    stream.close();
                }
                catch (IOException e) {
                    log.error(
                        "exception when closing a CRL file: " + file.getAbsolutePath());
                }
            }
        }

        return encoded;
    }
}
