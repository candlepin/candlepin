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
package org.fedoraproject.candlepin.pinsetter.tasks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.openssl.PEMWriter;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.SystemPrincipal;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.controller.CrlGenerator;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.inject.Inject;

/**
 * CertificateRevocationListTask.
 */
public class CertificateRevocationListTask implements Job {
    
    //"*/1 * * * * ?";
    /** The Constant DEFAULT_SCHEDULE. */
    public static final String DEFAULT_SCHEDULE = "* * */12 * * ?";
    
    /** The config. */
    private Config config;
    private CrlGenerator crlGenerator;
    
    private static Logger log = Logger.getLogger(CertificateRevocationListTask.class); 
    /**
     * Instantiates a new certificate revocation list task.
     * 
     * @param crlGenerator the generator
     * @param conf the conf
     */
    @Inject
    public CertificateRevocationListTask(CrlGenerator crlGenerator, Config conf) {
        this.crlGenerator = crlGenerator;
        this.config = conf;
    }

    /* (non-Javadoc)
     * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
     */
    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        String filePath = config.getString(ConfigProperties.CRL_FILE_PATH);
        log.info("Executing CRL Job. CRL filePath=" + filePath);
        File crlFile = new File(filePath);
        Principal systemPrincipal = new SystemPrincipal();
        ResteasyProviderFactory.pushContext(Principal.class, systemPrincipal);
        this.updateCRL(crlFile, "CN=test, UID=" + UUID.randomUUID());
        ResteasyProviderFactory.popContextData(Principal.class);
    }

    /**
     * Update crl.
     * 
     * @param in the in
     * @param principal the principal
     * @param out the out
     */
    public void updateCRL(InputStream in, String principal, OutputStream out) {
        try {
            X509CRL x509crl = null;
            if (in != null) {
                x509crl = (X509CRL) CertificateFactory.getInstance("X.509")
                    .generateCRL(in);
            }
            x509crl = this.crlGenerator.updateCRL(x509crl, principal);
            PEMWriter writer = new PEMWriter(new OutputStreamWriter(out));
            writer.writeObject(x509crl);
            writer.flush();
            writer.close();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Update crl.
     * 
     * @param file the file
     * @param principal the principal
     */
    public void updateCRL(File file, String principal) {
        FileInputStream in = null;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            if (file.exists() && file.length() > 0) {
                log.info("CRL File: " + file + " exists. Loading the old CRL");
                in = new FileInputStream(file);
            }
            else {
                log.info("CRL File: " + file + " either does not exist");
            }
            updateCRL(in, principal, stream);
            log.info("Completed generating CRL. Writing it to disk");
            FileUtils.writeByteArrayToFile(file, stream.toByteArray());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
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
