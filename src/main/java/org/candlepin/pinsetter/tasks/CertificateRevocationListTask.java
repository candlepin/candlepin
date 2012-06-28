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
import org.candlepin.util.CrlFileUtil;

import com.google.inject.Inject;

import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.File;
import java.io.IOException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.util.UUID;

/**
 * CertificateRevocationListTask.
 */
public class CertificateRevocationListTask implements Job {

    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    private Config config;


    private CrlFileUtil crlFileUtil;

    private static Logger log = Logger.getLogger(CertificateRevocationListTask.class);

    /**
     * Instantiates a new certificate revocation list task.
     *
     * @param crlFileUtil file util
     * @param conf the conf
     */
    @Inject
    public CertificateRevocationListTask(Config conf,
        CrlFileUtil crlFileUtil) {
        this.config = conf;
        this.crlFileUtil = crlFileUtil;
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
            crlFileUtil.updateCRLFile(crlFile, "CN=test, UID=" + UUID.randomUUID());
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



}
