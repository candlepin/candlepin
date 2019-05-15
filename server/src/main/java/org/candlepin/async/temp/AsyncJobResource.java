/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.async.temp;

import org.candlepin.auth.Verify;
import com.google.inject.Inject;
import io.swagger.annotations.ApiOperation;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.common.config.Configuration;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.OwnerServiceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * A simple test resource that will kick off an async job. This resource is only available for
 * testing and should be removed once the async job feature is completed.
 */
@Path("/async")
public class AsyncJobResource {

    private static Logger log = LoggerFactory.getLogger(AsyncJobResource.class);

    private Configuration config;
    private OwnerCurator ownerCurator;
    private OwnerServiceAdapter ownerService;
    private I18n i18n;
    private ModelTranslator translator;
    private JobManager jobManager;
    private AsyncJobStatusCurator jobCurator;

    @Inject
    public AsyncJobResource(OwnerCurator ownerCurator, I18n i18n, OwnerServiceAdapter ownerService,
        Configuration config, ModelTranslator translator, JobManager jobManager,
        AsyncJobStatusCurator jobCurator) {

        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.ownerService = ownerService;
        this.config = config;
        this.translator = translator;
        this.jobManager = jobManager;
        this.jobCurator = jobCurator;
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("test")
    @ApiOperation(notes = "A simple test resource that will kick off TestJob1 via the async job framework",
        value = "Run TestJob1")
    public AsyncJobStatusDTO forceFailure(
        @QueryParam("fail") @DefaultValue("false") Boolean forceFailure,
        @QueryParam("sleep") @DefaultValue("false") Boolean sleep,
        @QueryParam("persist") @DefaultValue("false") Boolean persist,
        @QueryParam("log_exec") @DefaultValue("true") Boolean logExec,
        @QueryParam("log_level") @DefaultValue("INFO") String logLevel,
        @QueryParam("retries") @DefaultValue("0") Integer retries)
        throws JobException {

        log.trace("TRACE MESSAGE");
        log.debug("DEBUG MESSAGE");
        log.info("INFO MESSAGE");
        log.warn("WARN MESSAGE");
        log.error("ERROR MESSAGE");

        AsyncJobStatus status = jobManager.queueJob(new JobConfig()
            .setJobKey("TEST_JOB1")
            .setJobGroup("async")
            .setJobName("Test Job 1")
            .setLogLevel(logLevel)
            .logExecutionDetails(logExec != null ? logExec.booleanValue() : true)
            .setRetryCount(retries != null ? retries.intValue() : 0)
            .setJobMetadata("build time", String.valueOf(System.currentTimeMillis()))
            .setJobMetadata("owner_key", "admin")
            .setJobMetadata("owner_id", "admin")
            .setJobMetadata("org", "admin")
            .setJobArgument("force_failure", forceFailure)
            .setJobArgument("sleep", sleep)
            .setJobArgument("persist", persist)
        );

        return this.translator.translate(status, AsyncJobStatusDTO.class);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{job_id}")
    @ApiOperation(notes = "Fetches the job info for a given job ID", value = "")
    public AsyncJobStatusDTO get(@PathParam("job_id") @Verify(AsyncJobStatus.class) String jobId) {
        AsyncJobStatus status = this.jobCurator.get(jobId);

        return this.translator.translate(status, AsyncJobStatusDTO.class);
    }

}
