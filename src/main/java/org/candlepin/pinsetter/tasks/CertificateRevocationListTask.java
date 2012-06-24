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
package org.candlepin.pinsetter.tasks;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.CrlGenerator;
import org.candlepin.pki.PKIUtility;

import com.google.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.UUID;

/**
 * CertificateRevocationListTask.
 */
public class CertificateRevocationListTask implements Job {

    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    private Config config;
    private CrlGenerator crlGenerator;
    private PKIUtility pkiUtility;

    private static Logger log = Logger.getLogger(CertificateRevocationListTask.class);

    /**
     * Instantiates a new certificate revocation list task.
     *
     * @param crlGenerator the generator
     * @param conf the conf
     */
    @Inject
    public CertificateRevocationListTask(CrlGenerator crlGenerator, Config conf,
        PKIUtility pkiUtility) {
        this.crlGenerator = crlGenerator;
        this.config = conf;
        this.pkiUtility = pkiUtility;
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        String filePath = config.getString(ConfigProperties.CRL_FILE_PATH);
        log.info("Executing CRL Job. CRL filePath=" + filePath);

        if (filePath == null) {
            throw new JobExecutionException("Invalid " +
                ConfigProperties.CRL_FILE_PATH, false);
        }

        try {
            File crlFile = new File(filePath);
            this.updateCRL(crlFile, "CN=test, UID=" + UUID.randomUUID());
        }
        catch (CRLException e) {
            log.error(e);
            throw new JobExecutionException(e, false);
        }
        catch (CertificateException e) {
            log.error(e);
            throw new JobExecutionException(e, false);
        }
        catch (IOException e) {
            log.error(e);
            throw new JobExecutionException(e, false);
        }
    }

    private void updateCRL(InputStream in, String principal, OutputStream out)
        throws IOException, CRLException, CertificateException {

        X509CRL x509crl = null;
        if (in != null) {
            x509crl = (X509CRL) CertificateFactory.getInstance("X.509")
                .generateCRL(in);
        }
        x509crl = this.crlGenerator.updateCRL(x509crl);
        out.write(pkiUtility.getPemEncoded(x509crl));
    }

    private void updateCRL(File file, String principal)
        throws CRLException, CertificateException, IOException {

        FileInputStream in = null;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            if (file.exists() && file.length() > 0) {
                log.info("CRL File: " + file + " exists. Loading the old CRL");
                in = new FileInputStream(file);
            }
            else {
                log.info("CRL File: " + file + " either does not exist or is empty.");
            }
            updateCRL(in, principal, stream);
            log.info("Completed generating CRL. Writing it to disk");
            FileUtils.writeByteArrayToFile(file, stream.toByteArray());
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

}
