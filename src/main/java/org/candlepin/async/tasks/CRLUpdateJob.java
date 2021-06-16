/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.async.tasks;

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.util.CrlFileUtil;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;



/**
 * The CRLUpdateJob synchronizes the CRL file with the DB, adding newly revoked certificates and
 * removing expired certificates from the CRL file.
 */
public class CRLUpdateJob implements AsyncJob {
    private static Logger log = LoggerFactory.getLogger(CRLUpdateJob.class);

    public static final String JOB_KEY = "CRLUpdateJob";
    public static final String JOB_NAME = "CRL Update";
    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    private Configuration config;
    private CrlFileUtil crlFileUtil;

    /**
     * Instantiates a new instance of the CRLUpdateJob
     *
     * @param conf
     *  the Candlepin configuration to use for this job
     *
     * @param crlFileUtil
     *  the CRLFileUtil instance to perform CRL-related tasks
     */
    @Inject
    public CRLUpdateJob(Configuration conf, CrlFileUtil crlFileUtil) {
        if (conf == null) {
            throw new IllegalArgumentException("conf is null");
        }

        if (crlFileUtil == null) {
            throw new IllegalArgumentException("crlFileUtil is null");
        }

        this.config = conf;
        this.crlFileUtil = crlFileUtil;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String filePath = config.getString(ConfigProperties.CRL_FILE_PATH, null);

        if (filePath == null || filePath.isEmpty()) {
            String errmsg = String.format("Invalid CRL path: %s", filePath);
            throw new JobExecutionException(errmsg, true);
        }

        try {
            log.info("Updating CRL file: {}", filePath);

            File crlFile = new File(filePath);
            this.crlFileUtil.syncCRLWithDB(crlFile);

            context.setJobResult("CRL Update completed successfully");
        }
        catch (IOException e) {
            throw new JobExecutionException(e, true);
        }
    }
}
