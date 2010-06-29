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

import java.util.Date;
import java.util.List;

import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.inject.Inject;

/**
 * CertificateRevocationListTask
 */
public class CertificateRevocationListTask implements Job {
    public static final String DEFAULT_SCHEDULE = "*/1 * * * * ?";
    private EntitlementCertificateCurator entCertCurator;

    /**
     * @param entCertCurator the entCertCurator to set
     */
    public void setEntCertCurator(EntitlementCertificateCurator entCertCurator) {
        this.entCertCurator = entCertCurator;
    }

    @Inject
    public CertificateRevocationListTask(
        EntitlementCertificateCurator entCertCurator) {
        this.entCertCurator = entCertCurator;
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        List<EntitlementCertificate> entCerts = entCertCurator.listAll();
         System.out.println("crl task ran: " + new Date().toString());
    }
    
    

}
