/*
 * Default Candlepin rule set.
 */

var SOCKET_FACT="cpu.cpu_socket(s)";

function entitlement_name_space() {
    return Entitlement;
}

function consumer_delete_name_space() {
    return ConsumerDelete;
}

function pool_name_space() {
    return Pool;
}

function export_name_space() {
    return Export;
}

/* Utility functions */
function contains(a, obj) {
    for (var i = 0; i < a.length; i++) {
        var result = a[i] == obj;
        if (result) {
            return true;
        }
    }
    return false;
}

function containsAll(a, b) {
    for (var i = 0 ; i < b.length ; i++) {
        if (!contains(a, b[i])) {
            return false;
        }
    }

    return true;
}

function getRelevantProvidedProducts(pool, products) {
    var provided = [];

    for (var i = 0 ; i < products.length ; i++) {
        var product = products[i];
        if (pool.provides(product.getId())) {
            provided.push(product);
        }
    }

    return provided;
}

function providesSameProducts(products1, products2) {
    return containsAll(products1, products2) && containsAll(products2, products1);
}

function arrayToString(a) {
    msg = "[";
    for each (q in a) {
        msg += q.getId() + " ";
    }
    msg += "]";
    return msg;
}

// XXX i don't know what this is really called
function recursiveCombination(a, n) {
    if (a.length == 0) {
        return [];
    }

    var res = [];
    for each (x in recursiveCombination(a.slice(1), n)) {
        if (x.length <= n) {
            res.push(x);
        }
        if (x.length + 1 <= n) {
            var z = x.slice(0);
            z.push(a[0]);
            res.push(z);
        }
    }
    res.push([a[0]]);
    return res;
}


// Check if the provided list of pools contains any duplicated products
// We don't need to worry about checking multi-entitle allowed products,
// as you can use as many of those as you want.
function hasNoProductOverlap(combination) {
    var seen_product_ids = [];
    for each (pool in combination) {
        var products = pool.products;
        for (var i = 0 ; i < products.length ; i++) {
            var product = products[i];
            if (!contains(seen_product_ids, product.id)) {
                seen_product_ids.push(product.id);
            } else if (product.getAttribute("multi-entitlement") != "yes") {
                return false;
            }
        }
    }

    return true;
}

function architectureMatches(product, consumer) {
    var supportedArches = [];
    var archString = product.getAttribute('arch');
    if (archString != null) {
        supportedArches = archString.toUpperCase().split(prodAttrSeparator);

        supportedArches = new java.util.HashSet(java.util.Arrays.asList(supportedArches));

        // If X86 is supported, add all variants to this list:
        if (supportedArches.contains("X86")) {
           supportedArches.add("I386");
           supportedArches.add("I586");
           supportedArches.add("I686");
        }

        if(!supportedArches.contains('ALL') &&
           (!consumer.hasFact("uname.machine")  ||
            !supportedArches.contains(consumer.getFact('uname.machine').toUpperCase())
            )
          ){
           return false;
       }
   }

   return true;
}

function findStackingPools(pool, consumer, products){
    log.debug("\nFindStackingPools " + pool + " " + consumer + " \n");
    var consumer_sockets = 1;
    if (consumer.hasFact(SOCKET_FACT)) {
        consumer_sockets = consumer.getFact(SOCKET_FACT);
    }

    var new_pools = [];
    for each (provided_product in getRelevantProvidedProducts(pool, products)) {
        log.debug("provided_product-- " + provided_product + " " + provided_product.getName());
        if (!( (provided_product.getAttribute("multi-entitlement") &&
                (provided_product.getAttribute("stacking_id"))))) {
            continue;
        }

        var product_sockets = 0;
        if (provided_product.getAttribute("multi-entitlement") && provided_product.getAttribute("stacking_id")) {
            log.debug("product: " +  provided_product + "is stackable and multi-entitled");
            log.debug("consumer_sockets " + consumer_sockets);
            log.debug("product_sockets " + product_sockets);
            log.debug("provided_product.getAttribute(sockets) " + provided_product.getAttribute("sockets"));
            while (product_sockets < consumer_sockets) {
                new_pools.push(pool);
                product_sockets += parseInt(provided_product.getAttribute("sockets"));
                log.debug("product_sockets2 " + product_sockets);
                //break;
            }
        }
    }
    return new_pools;

}

function hasAllMultiEntitlement(combination) {
    for each (pool in combination) {
        var products = pool.products;
        for (var i = 0 ; i < products.length ; i++) {
            var product = products[i];
            if (product.getAttribute("multi-entitlement") != "yes") {
                return false;
            }
        }
    }
    return true;
}

function splitStackingPools(combination) {
    var stackable_pools = [];
    var other_pools = [];
//    var stackable = false;
    for each (pool in combination) {
        log.debug("pool " + pool.getId());
        var products = pool.products;
        var stackable = false;
        for (var i = 0 ; i < products.length ; i++) {
            var product = products[i];
            log.debug("\nproduct " + product.getName());
            log.debug("multi-entitlement " + product.getAttribute("multi-entitlement") );
            log.debug("stacking_id " + product.getAttribute("stacking_id") );
            if ((product.getAttribute("multi-entitlement") == "yes") && (product.getAttribute("stacking_id"))){
                log.debug("this product is stackable");
                stackable = true;
                }
        }
        if (stackable) {
            stackable_pools.push(pool);
        } else {
            other_pools.push(pool);
        }
    }
    return {'stackable':stackable_pools, 'other':other_pools };
}

var Entitlement = {

    // defines mapping of product attributes to functions
    // the format is: <function name>:<order number>:<attr1>:...:<attrn>, comma-separated ex.:
    // func1:1:attr1:attr2:attr3, func2:2:attr3:attr4
    attribute_mappings: function() {
        return  "architecture:1:arch," +
            "sockets:1:sockets," +
            "requires_consumer_type:1:requires_consumer_type," +
            "user_license:1:user_license," +
            "virt_only:1:virt_only," +
            "virt_limit:1:virt_limit";
    },

    pre_virt_only: function() {
        var virt_pool = 'true'.equals(attributes.get('virt_only'));
        var guest = false;
        if (consumer.hasFact('virt.is_guest')) {
            guest = 'true'.equals(consumer.getFact('virt.is_guest'));
        }


        if (virt_pool) {
            if (!guest) {
                pre.addError("rulefailed.virt.only");
            }
            else {
                // At this point we know this is a virt only pool and the requesting
                // consumer is a guest, check if there are host restrictions on the pool:
                if (pool.getSourceEntitlement() != null) {
                    var hostConsumer = pre.getHostConsumer(consumer.getUuid());

                    if (hostConsumer == null || !hostConsumer.getUuid().equals(pool.getSourceEntitlement().getConsumer().getUuid())) {
                            pre.addError("virt.guest.host.does.not.match.pool.owner");
                    }
                }
            }
        }
    },

    post_user_license: function() {
        // Default to using the same product from the pool.
        var productId = pool.getProductId();

        // Check if the sub-pool should be for a different product:
        if (attributes.containsKey("user_license_product")) {
            productId = attributes.get("user_license_product");
        }

        // Create a sub-pool for this user
        post.createUserRestrictedPool(productId, pool,
                                      attributes.get("user_license"));
    },

    pre_requires_consumer_type: function() {
        if (!attributes.get("requires_consumer_type").equals(consumer.getType())) {
            pre.addError("rulefailed.consumer.type.mismatch");
        }
    },

    pre_virt_limit: function() {
    },

    post_virt_limit: function() {
        if (attributes.containsKey("virt_limit") && standalone) {
            var productId = pool.getProductId();
	        var virt_limit = attributes.get("virt_limit");
	        var virt_attributes = new java.util.HashMap();
	        virt_attributes.put("virt_only", "true");
	        virt_attributes.put("pool_derived", "true");

	        if ('unlimited'.equals(virt_limit)) {
	            post.createParentConsumerRestrictedPool(productId, pool,
	                                        'unlimited', virt_attributes);
	        } else {
	            var virt_quantity = parseInt(virt_limit) * entitlement.getQuantity();
	            if (virt_quantity > 0) {
	                post.createParentConsumerRestrictedPool(productId, pool,
	                                            virt_quantity.toString(), virt_attributes);
	            }
	        }
	    }
    },

    pre_architecture: function() {
       if (!architectureMatches(product, consumer)) {
         pre.addWarning("rulewarning.architecture.mismatch");
       }
    },

    post_architecture: function() {
    },

    pre_sockets: function() {
        if (!consumer.hasFact(SOCKET_FACT) ||
            (parseInt(product.getAttribute("sockets")) < parseInt(consumer.getFact(SOCKET_FACT))) &&
            (!product.hasAttribute("stacking_id"))) {
            pre.addWarning("rulewarning.unsupported.number.of.sockets");
        }
    },

    post_sockets: function() {
    },

    pre_global: function() {
        if (consumer.hasEntitlement(pool.getId()) && product.getAttribute("multi-entitlement") != "yes") {
            pre.addError("rulefailed.consumer.already.has.product");
        }

		if (pre.getQuantity() > 1 && product.getAttribute("multi-entitlement") != "yes") {
			pre.addError("rulefailed.pool.does.not.support.multi-entitlement");
		}

		// If the product has no required consumer type, assume it is restricted to "system":
		if (!product.hasAttribute("requires_consumer_type")) {
			if (!consumer.getType().equals("system")) {
				pre.addError("rulefailed.consumer.type.mismatch");
			}

        }

        if (pool.getRestrictedToUsername() != null && !pool.getRestrictedToUsername().equals(consumer.getUsername())) {
            pre.addError("pool.not.available.to.user, pool= '" + pool.getRestrictedToUsername() + "', actual username='" + consumer.getUsername() + "'" );
        }

        // FIXME
        // for auto sub stacking, we need to be able to pull across multiple
        // pools eventually, so this would need to go away in that case
        pre.checkQuantity(pool);
    },

    post_global: function() {
    },

    select_pool_global: function() {
        // Greedy selection for now, in order
        // XXX need to watch out for multientitle products

        var best_in_class_pools = [];
        var product_sockets = 0;
        log.debug("pool length " + pools.length);
        for each (pool in pools) {
            log.debug("start pool: " + pool.getId());
        }

        var consumer_sockets = 1;
        if (consumer.hasFact(SOCKET_FACT)) {
            consumer_sockets = consumer.getFact(SOCKET_FACT);
        }

        for (var i = 0 ; i < pools.length ; i++) {
            var pool = pools[i];
            log.debug("pool.getTopLevelProduct " + (pool.getTopLevelProduct()));
            if (architectureMatches(pool.getTopLevelProduct(), consumer)) {
                log.debug("pool.id " + pool.getId());
                var provided_products = getRelevantProvidedProducts(pool, products);
                for each (pp in provided_products){
                    log.debug("provided_products " + pp.getName());
                    }
                // XXX wasteful, should be a hash or something.
                var duplicate_found = false;

                for each (best_pool in best_in_class_pools) {
                    var best_provided_products = getRelevantProvidedProducts(best_pool, products);

                    if (providesSameProducts(provided_products, best_provided_products)) {
                        duplicate_found = true;
                        log.debug("provides same product" + pool.getId());

                        // Prefer a virt_only pool over a regular pool, else fall through to the next rules.
                        // At this point virt_only pools will have already been filtered out by the pre rules
                        // for non virt machines.
                        if (pool.getAttribute("virt_only") == "true" && best_pool.getAttribute("virt_only") != "true") {
                            log.debug("selecting virt-only pool " + pool.getId());
                            best_in_class_pools[best_in_class_pools.indexOf(best_pool)] = pool;
                            log.debug("virt-only");
                            break;
                        }
                        else if (best_pool.getAttribute("virt_only") == "true" && pool.getAttribute("virt_only") != "true") {
                            log.debug("not a virt-only pool");
                            break;
                        }

                        // If two pools are equal, select the pool that expires first
                        if (best_pool.getEndDate().after(pool.getEndDate())) {
                            log.debug("selecting expiring pool " + pool.getId());
                            best_in_class_pools[best_in_class_pools.indexOf(best_pool)] = pool;
                            break;
                        }
                        // only if pool is new? aka, not in best pools yet
                        var new_pools = findStackingPools(pool, consumer, products);
                        best_in_class_pools.concat(new_pools);
                        for each (new_pool in new_pools){
                            log.debug("selecting new_pool: " + new_pool.getId());
                        }

                        log.debug("other");
                    }
                }
                if (!duplicate_found) {
                    log.debug("no duplicate");
                    //var new_pools = [];
                    var new_pools = findStackingPools(pool, consumer, products);
                    if (new_pools.length > 0){
                        log.debug("selecting new pools, no dups " + new_pools);
                        best_in_class_pools = new_pools;
                        break;
                        }
                    else {
                        log.debug("selecting new pools, no new_pools length");
                        best_in_class_pools.push(pool);
                    }
                }
            }
        }


        var pools_info = splitStackingPools(best_in_class_pools);
        log.debug("pools_info stackable " + pools_info['stackable']);
        log.debug("pools_info other " + pools_info['other']);

        var candidate_combos = recursiveCombination(pools_info['other'], products.length)

        log.debug("Selecting " + products.length + " products from " + best_in_class_pools.length +
                  " pools in " + candidate_combos.length + " possible combinations");

        // Select the best pool combo. We prefer:
        // -The combo that provides the most products
        // -The combo that uses the fewest entitlements

        var selected_pools = [];
        selected_pools = best_in_class_pools;
        var best_provided_count = 0;

        for each (pool_combo in candidate_combos) {
            var provided_count = 0;
            var unique_provided = [];
            log.debug("checking pool_combo " + pool_combo);
            for each (pool in pool_combo) {
                log.debug("\tpool_combo " + pool.getId());
                var provided_products = getRelevantProvidedProducts(pool, products);
                for each (provided_product in provided_products) {
                    log.debug("\t\tprovided_product " + provided_product.getId());
                   if (provided_product.getAttribute("multi-entitlement")) {
                       log.debug("pool_combo select multi-entitlement");
                       unique_provided.push(provided_product);
                   }
                    // find all the pools that provide a product nothing else does
                    if (!contains(unique_provided, provided_product)) {
                        log.debug("pool_combo unique pools " + provided_product);
                        unique_provided.push(provided_product);
                    }
                }
            }

            for each (product in unique_provided){
                log.debug("unique_provided " + product.getId() + " " + product.getName());
            }
            // number of pools is less than the MIN pools
            if (unique_provided.length < best_provided_count) {
                continue;
            } else if (unique_provided.length > best_provided_count || pool_combo.length < selected_pools.length) {
                if (hasNoProductOverlap(pool_combo)) {
                    selected_pools = pool_combo;
                    best_provided_count = unique_provided.length;
                }
                selected_pools = pool_combo;
            }
        }

        selected_pools.concat(pools_info['stackable']);

        for each (pool in selected_pools){
            log.debug("selected_pool2 " + pool + "  " + pool.getId());
        }

        // We may not have selected pools for all products; that's ok.
        if (selected_pools.length > 0) {
            return selected_pools;
        }

        return null;
    }
}

var ConsumerDelete = {
    global: function() {
        if (consumer.getType() == "person") {
            helper.deleteUserRestrictedPools(consumer.getUsername());
        }
    }
}

var Pool = {

    /*
     * Creates all appropriate pools for a subscription.
     */
    createPools: function () {
        var pools = new java.util.LinkedList();
        var quantity = sub.getQuantity() * sub.getProduct().getMultiplier();
        var providedProducts = new java.util.HashSet();
        var newPool = new org.fedoraproject.candlepin.model.Pool(sub.getOwner(), sub.getProduct().getId(),
                sub.getProduct().getName(), providedProducts,
                    quantity, sub.getStartDate(), sub.getEndDate(), sub.getContractNumber(),
                    sub.getAccountNumber());
        if (sub.getProvidedProducts() != null) {
            for each (var p in sub.getProvidedProducts().toArray()) {
                var providedProduct = new org.fedoraproject.candlepin.model.
                    ProvidedProduct(p.getId(), p.getName());
                providedProduct.setPool(newPool);
                providedProducts.add(providedProduct);
            }
        }
        helper.copyProductAttributesOntoPool(sub, newPool);
        newPool.setSubscriptionId(sub.getId());
        pools.add(newPool);

        // Check if we need to create a virt-only pool for this subscription:
        if (attributes.containsKey("virt_limit") && !standalone) {
            var virt_limit = attributes.get("virt_limit");
            var virt_attributes = new java.util.HashMap();
            virt_attributes.put("virt_only", "true");
            virt_attributes.put("pool_derived", "true");
            // Make sure the virt pool does not have a virt_limit,
            // otherwise this will recurse infinitely
            virt_attributes.put("virt_limit", "0");

            if ('unlimited'.equals(virt_limit)) {
                pools.add(helper.createPool(sub, sub.getProduct().getId(),
                                            'unlimited', virt_attributes));
            } else {
                var virt_limit_quantity = parseInt(virt_limit);

                if (virt_limit_quantity > 0) {
                    var virt_quantity = sub.getQuantity() * virt_limit_quantity;

                    pools.add(helper.createPool(sub, sub.getProduct().getId(),
                                                virt_quantity.toString(), virt_attributes));
                }
            }
        }
        return pools;
    },

    /*
     * Updates the existing pools for a subscription.
     */
    updatePools: function () {
        var poolsUpdated = new java.util.LinkedList();
        for each (var existingPool in pools.toArray()) {
            log.info("Updating pool: " + existingPool.getId());
            var datesChanged = (!sub.getStartDate().equals(
                existingPool.getStartDate())) ||
                (!sub.getEndDate().equals(existingPool.getEndDate()));
            // Expected quantity is normally the subscription's quantity, but for
            // virt only pools we expect it to be sub quantity * virt_limit:
            var expectedQuantity = sub.getQuantity() * sub.getProduct().getMultiplier();
            if (existingPool.hasAttribute("virt_only") &&
                existingPool.getAttributeValue("virt_only").equals("true")) {
                // Assuming there mere be a virt limit attribute set:
                var virt_limit = attributes.get("virt_limit");

                if ('unlimited'.equals(virt_limit)) {
                    // Bad to hardcode this conversion here
                    // TODO:  Figure out a better way translate this value!
                    expectedQuantity = -1;
                } else {
                    expectedQuantity = sub.getQuantity() * parseInt(virt_limit);
                }
            }

            var quantityChanged = !(expectedQuantity == existingPool.getQuantity());
            var productsChanged = helper.checkForChangedProducts(existingPool, sub);

            var poolAttributesChanged = helper.copyProductAttributesOntoPool(sub,
                                                                             existingPool);
            if (poolAttributesChanged) {
                log.info("Updated pool attributes from subscription.");
            }

            if (!(quantityChanged || datesChanged || productsChanged ||
                  poolAttributesChanged)) {
                //TODO: Should we check whether pool is overflowing here?
                log.info("   No updates required.");
                continue;
            }

            if (quantityChanged) {
                log.info("   Quantity changed to: " + expectedQuantity);
                existingPool.setQuantity(expectedQuantity);
            }

            if (datesChanged) {
                log.info("   Subscription dates changed.");
                existingPool.setStartDate(sub.getStartDate());
                existingPool.setEndDate(sub.getEndDate());
            }

            if (productsChanged) {
                log.info("   Subscription products changed.");
                existingPool.setProductName(sub.getProduct().getName());
                existingPool.getProvidedProducts().clear();

                if (sub.getProvidedProducts() != null) {
                    for each (var p in sub.getProvidedProducts().toArray()) {
                        var providedProduct = new org.fedoraproject.candlepin.model.
                            ProvidedProduct(p.getId(), p.getName());
                        existingPool.addProvidedProduct(providedProduct);
                    }
                }
            }
            poolsUpdated.add(new org.fedoraproject.candlepin.policy.js.pool.PoolUpdate(
                                 existingPool, datesChanged, quantityChanged, productsChanged));
        }
        return poolsUpdated;
    }

}

var Export = {
    can_export_entitlement: function() {
        pool_derived = attributes.containsKey('pool_derived') &&
                    'true'.equals(attributes.get('pool_derived'));

        return !consumer.getType().isManifest() || !pool_derived;
    }
}
