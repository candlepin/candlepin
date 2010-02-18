package org.fedoraproject.candlepin.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;

import com.google.inject.Inject;

@Path("/admin")
public class AdminResource {

    private ConsumerTypeCurator consumerTypeCurator;

    @Inject
    public AdminResource(ConsumerTypeCurator consumerTypeCurator) {
        this.consumerTypeCurator = consumerTypeCurator;
    }

    @GET
    @Produces({MediaType.TEXT_PLAIN})
    @Path("init")
    public String initialize() {

        // First, determine if we've already setup the DB and if so, do *nothing*!
        ConsumerType systemType = consumerTypeCurator.lookupByLabel(ConsumerType.SYSTEM);
        if (systemType != null) {
            return "Already initialized.";
        }

        ConsumerType system = new ConsumerType(ConsumerType.SYSTEM);
        consumerTypeCurator.create(system);

        ConsumerType virtSystem = new ConsumerType(ConsumerType.VIRT_SYSTEM);
        consumerTypeCurator.create(virtSystem);

        return "Initialized!";
    }
}
