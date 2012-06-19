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
package org.candlepin.resteasy.interceptor;

import org.candlepin.auth.Principal;
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.pinsetter.core.PinsetterException;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.jboss.resteasy.util.HttpResponseCodes;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.impl.JobDetailImpl;

import javax.ws.rs.ext.Provider;

/**
 * Resteasy interceptor that handles scheduling a one-time pinsetter job if the
 * server response returns a {@link JobDetail} object.  This also signifies that
 * the response should be asynchronous, and the response code and response data
 * is set appropriately so that the client can query the job status as a later
 * time.
 */
@Provider
@ServerInterceptor
public class PinsetterAsyncInterceptor implements PostProcessInterceptor {

    private PinsetterKernel pinsetterKernel;
    private com.google.inject.Provider<Principal> principalProvider;

    @Inject
    public PinsetterAsyncInterceptor(PinsetterKernel pinsetterKernel,
        com.google.inject.Provider<Principal> principalProvider) {
        this.pinsetterKernel = pinsetterKernel;
        this.principalProvider = principalProvider;
    }

    /**
     * {@inheritDoc}
     *
     * @param response the server response (provided by Resteasy)
     */
    @Override
    public void postProcess(ServerResponse response) {
        Object entity = response.getEntity();

        if (entity instanceof JobDetail) {
            JobDetail jobDetail = (JobDetail) entity;
            setJobPrincipal(jobDetail);

            try {
                JobStatus status = this.pinsetterKernel.scheduleSingleJob(jobDetail);

                response.setEntity(status);
                response.setStatus(HttpResponseCodes.SC_ACCEPTED);
            }
            catch (PinsetterException e) {
                throw new ServiceUnavailableException("Error scheduling refresh job.", e);
            }
        }
    }

    private void setJobPrincipal(JobDetail jobDetail) {
        JobDataMap map = jobDetail.getJobDataMap();
        if (map == null) {
            map = new JobDataMap();
        }

        map.put(PinsetterJobListener.PRINCIPAL_KEY, this.principalProvider.get());
        JobDetailImpl impl = (JobDetailImpl) jobDetail;
        impl.setJobDataMap(map);
    }

}
