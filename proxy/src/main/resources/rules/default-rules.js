/*
 * Default Candlepin rule set.
 */


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
	   var result = a[i] == obj
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
		var product = products[i]
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
           (!consumer.hasFact("cpu.architecture")  ||
            !supportedArches.contains(consumer.getFact('cpu.architecture').toUpperCase())
            )
          ){
           return false;
       }
   }

   return true;
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
                "virt_only:1:virt_only";
	},

    pre_virt_only: function() {
        var virt_pool = 'true'.equals(attributes.get('virt_only'));
        var guest = false;
        
        if (consumer.hasFact('virt.is_guest')) {
            guest = 'true'.equals(consumer.getFact('virt.is_guest'));
        }

        if (virt_pool && !guest) {
           pre.addError("rulefailed.virt.only"); 
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

	pre_architecture: function() {
       if (!architectureMatches(product, consumer)) {
         pre.addWarning("rulewarning.architecture.mismatch");
       }
	},

	post_architecture: function() {
	},

	pre_sockets: function() {
		if (!consumer.hasFact("cpu.cpu_socket(s)") ||
		    (parseInt(product.getAttribute("sockets")) < parseInt(consumer.getFact("cpu.cpu_socket(s)")))) {
			pre.addWarning("rulewarning.unsupported.number.of.sockets");
		}
	},

	post_sockets: function() {
	},

	pre_global: function() {
		if (consumer.hasEntitlement(pool.getId()) && product.getAttribute("multi-entitlement") != "yes") {
			pre.addError("rulefailed.consumer.already.has.product");
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

        pre.checkQuantity(pool);
	},

	post_global: function() {
	},

	select_pool_global: function() {
		// Greedy selection for now, in order
		// XXX need to watch out for multientitle products

		// pools that have been filtered by expiration date, etc
		var best_in_class_pools = [];
		for (var i = 0 ; i < pools.length ; i++) {
            var pool = pools[i];
            if (architectureMatches(pool.getTopLevelProduct(), consumer)) {
                var provided_products = getRelevantProvidedProducts(pool, products);
                // XXX wasteful, should be a hash or something.
                var duplicate_found = false;
                for each (best_pool in best_in_class_pools) {
                    var best_provided_products = getRelevantProvidedProducts(best_pool, products);
                    if (providesSameProducts(provided_products, best_provided_products)) {
                    	duplicate_found = true;
                    	
                    	// Prefer a virt_only pool over a regular pool, else fall through to the next rules.
                    	// At this point virt_only pools will have already been filtered out by the pre rules
                    	// for non virt machines.
                    	if (pool.getAttribute("virt_only") == "true" && best_pool.getAttribute("virt_only") != "true") {
                    		best_in_class_pools[best_in_class_pools.indexOf(best_pool)] = pool;
                            break;
                    	}
                    	else if (best_pool.getAttribute("virt_only") == "true" && pool.getAttribute("virt_only") != "true") {
                    		break;
                    	}
                    	
                        // If two pools are equal, select the pool that expires first
                        if (best_pool.getEndDate().after(pool.getEndDate())) {
                            best_in_class_pools[best_in_class_pools.indexOf(best_pool)] = pool;
                            break;
                        }
                        // Autobind 2 logic goes here
                    }
                }

                if (!duplicate_found) {
                    best_in_class_pools.push(pool);
                }
            }
		}

		candidate_combos = recursiveCombination(best_in_class_pools, products.length);
		log.debug("Selecting " + products.length + " products from " + best_in_class_pools.length +
				" pools in " + candidate_combos.length + " possible combinations");
		// Select the best pool combo. We prefer:
		// -The combo that provides the most products
		// -The combo that uses the fewest entitlements

		var selected_pools = [];
		var best_provided_count = 0;

		for each (pool_combo in candidate_combos) {
			var provided_count = 0;
			var unique_provided = [];
			for each (pool in pool_combo) {
				var provided_products = getRelevantProvidedProducts(pool, products);
				for each (provided_product in provided_products) {
					if (!contains(unique_provided, provided_product)) {
						unique_provided.push(provided_product);
					}
				}
			}

			if (unique_provided.length < best_provided_count) {
				continue;
			} else if (unique_provided.length > best_provided_count || pool_combo.length < selected_pools.length) {
				if (hasNoProductOverlap(pool_combo)) {
					selected_pools = pool_combo;
					best_provided_count = unique_provided.length;
				}
			}
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
        newPool.setSubscriptionId(sub.getId());
        pools.add(newPool);

        // Check if we need to create a virt-only pool for this subscription:
        if (attributes.containsKey("virt_limit")) {
            var virt_limit = attributes.get("virt_limit");
            var virt_attributes = new java.util.HashMap();
            virt_attributes.put("virt_only", "true");
            // Make sure the virt pool does not have a virt_limit,
            // otherwise this is recurse infinitely
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
            	var virtLimit = parseInt(attributes.get("virt_limit"));
            	expectedQuantity = sub.getQuantity() * virtLimit;
            }
            
            var quantityChanged = !(expectedQuantity == existingPool.getQuantity());
            var productsChanged = helper.checkForChangedProducts(existingPool, sub);
            
            if (!(quantityChanged || datesChanged || productsChanged)) {
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
        return !attributes.containsKey('virt_only') ||
            !'true'.equals(attributes.get('virt_only'));
    }
}
