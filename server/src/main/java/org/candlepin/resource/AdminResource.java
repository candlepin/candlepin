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
package org.candlepin.resource;

import org.candlepin.audit.HornetqEventDispatcher;
import org.candlepin.audit.QueueStatus;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SystemPrincipal;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultUserServiceAdapter;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Candlepin server administration REST calls.
 */
@Path("/admin")
public class AdminResource {

    private static Logger log = LoggerFactory.getLogger(AdminResource.class);

    private UserServiceAdapter userService;
    private UserCurator userCurator;

    private ProductServiceAdapter productService;
    private ProductCurator productCurator;
    private ContentCurator contentCurator;
    private PoolCurator poolCurator;

    private HornetqEventDispatcher dispatcher;

    @Inject
    public AdminResource(UserServiceAdapter userService, UserCurator userCurator,
        ProductServiceAdapter productService, ProductCurator productCurator,
        ContentCurator contentCurator, PoolCurator poolCurator,
        HornetqEventDispatcher dispatcher) {

        this.userService = userService;
        this.userCurator = userCurator;

        this.productService = productService;
        this.productCurator = productCurator;
        this.contentCurator = contentCurator;
        this.poolCurator = poolCurator;

        this.dispatcher = dispatcher;
    }

    /**
     * Initializes the Candlepin database
     * <p>
     * Currently this just creates the admin user for standalone deployments using the
     * default user service adapter. It must be called once after candlepin is installed,
     * repeat calls are not required, but will be harmless.
     * <p>
     * The String returned is the description if the db was or already is initialized.
     *
     * @return a String object
     * @httpcode 200
     */
    @GET
    @Produces({MediaType.TEXT_PLAIN})
    @Path("init")
    @SecurityHole(noAuth = true)
    public String initialize() {
        log.debug("Called initialize()");

        log.info("Initializing Candlepin database.");

        // All we really need to do here is create the initial admin user, if we're using
        // the default user service adapter, and no other users exist already:
        if (userService instanceof DefaultUserServiceAdapter &&
            userCurator.getUserCount() == 0) {
            // Push the system principal so we can create all these entries as a
            // superuser:
            ResteasyProviderFactory.pushContext(Principal.class, new SystemPrincipal());

            log.info("Creating default super admin.");
            User defaultAdmin = new User("admin", "admin", true);
            userService.createUser(defaultAdmin);
            return "Initialized!";
        }
        else {
            // Any other user service adapter and we really have nothing to do:
            return "Already initialized.";
        }
    }

    /**
     * @return Basic information on the HornetQ queues and how many messages are
     * pending in each.
     *
     * NOTE: This does not report on any pending messages in the AMQP bus.
     *
     * @httpcode 200
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("queues")
    public List<QueueStatus> getQueueStats() {
        return dispatcher.getQueueInfo();
    }



    @GET
    @Produces({MediaType.TEXT_PLAIN})
    @Path("pophosteddb")
    public String populatedHostedDB() {
        int pcount = 0;
        int ccount = 0;
        log.info("Populating Hosted DB");

        String sql =
            "SELECT DISTINCT \"product_id\" FROM (" +
            "  SELECT \"product_id\" FROM \"cp_pool_products\"" +
            "  UNION" +
            "  SELECT \"productid\" AS \"product_id\" FROM \"cp_pool\"" +
            "  UNION" +
            "  SELECT \"derivedproductid\" AS \"product_id\" FROM \"cp_pool\"" +
            ") u WHERE u.product_id IS NOT NULL AND u.product_id != '';";

        Set<String> productIds = this.poolCurator.getAllKnownProductIds();

        for (Product product : this.productService.getProductsByIds(productIds)) {
            log.info("  Updating product: " + product.toString());
            this.productCurator.createOrUpdate(product);
            ++pcount;

            for (ProductContent pcontent : product.getProductContent()) {
                log.info("    Inserting product content: " + pcontent.getContent().toString());
                this.contentCurator.createOrUpdate(pcontent.getContent());
                ++ccount;
            }
        }

        log.info("Finished populating Hosted DB");
        return String.format("DB populated successfully. Inserted/updated %d products and %d content", pcount, ccount);
    }


}
