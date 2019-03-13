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

import com.google.inject.Inject;
import io.swagger.annotations.ApiOperation;
import org.candlepin.async.JobBuilder;
import org.candlepin.async.JobManager;
import org.candlepin.common.config.Configuration;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.OwnerServiceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
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

    @Inject
    public AsyncJobResource(OwnerCurator ownerCurator, I18n i18n, OwnerServiceAdapter ownerService,
        Configuration config, ModelTranslator translator, JobManager jobManager) {
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.ownerService = ownerService;
        this.config = config;
        this.translator = translator;
        this.jobManager = jobManager;
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("test")
    @ApiOperation(notes = "A simple test resource that will kick off TestJob1 via the async job framework",
        value = "Run TestJob1")
    public AsyncJobStatus forceFailure(@QueryParam("fail") @DefaultValue("false") Boolean forceFailure,
        @QueryParam("sleep") @DefaultValue("false") Boolean sleep, @QueryParam("persist")
        @DefaultValue("false") Boolean persist) {

        // for (int j = 0; j < 10000; ++j) {
        //     JobBuilder builder = new JobBuilder()
        //         .setJobKey("TEST_JOB-" + j)
        //         .setJobGroup("async")
        //         .setJobName("Test Job " + j)
        //         .setJobArgument("force_failure", forceFailure)
        //         .setJobArgument("sleep", sleep)
        //         .setJobArgument("persist", persist);

        //     // Add a ton of random constraints for performance testing
        //     for (int i = 0; i < 100; ++i) {
        //         builder.setUniqueConstraint("constraint-" + i, "value-" + i);
        //     }

        //     jobManager.queueJob(builder);
        // }

        // return null;


        JobBuilder builder = new JobBuilder()
            .setJobKey("TEST_JOB")
            .setJobGroup("async")
            .setJobName("Test Job")
            .setJobArgument("force_failure", forceFailure)
            .setJobArgument("sleep", sleep)
            .setJobArgument("persist", persist);

        // Add a ton of random constraints for performance testing
        for (int i = 0; i < 100; ++i) {
            builder.setUniqueConstraint("constraint-" + i, "value-" + i);
        }

        return jobManager.queueJob(builder);



    }

}
