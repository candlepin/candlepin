package org.fedoraproject.candlepin.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;

import com.google.inject.Inject;

@Path("/admin")
public class AdminResource {

    private ConsumerTypeCurator consumerTypeCurator;
    private static Logger log = Logger.getLogger(AdminResource.class);

    @Inject
    public AdminResource(ConsumerTypeCurator consumerTypeCurator) {
        this.consumerTypeCurator = consumerTypeCurator;
    }

    @GET
    @Produces({MediaType.TEXT_PLAIN})
    @Path("init")
    public String initialize() {
        
        log.debug("Called initialize()");

        // First, determine if we've already setup the DB and if so, do *nothing*!
        ConsumerType systemType = consumerTypeCurator.lookupByLabel(ConsumerType.SYSTEM);
        if (systemType != null) {
            log.info("Database already initialized.");
            return "Already initialized.";
        }
        log.info("Initializing Candlepin database.");

        ConsumerType system = new ConsumerType(ConsumerType.SYSTEM);
        consumerTypeCurator.create(system);
        log.debug("Created: " + system);

        ConsumerType virtSystem = new ConsumerType(ConsumerType.VIRT_SYSTEM);
        consumerTypeCurator.create(virtSystem);
        log.debug("Created: " + virtSystem);

        return "Initialized!";
    }
}
