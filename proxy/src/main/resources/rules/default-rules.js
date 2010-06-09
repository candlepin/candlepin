/*
 * Default Candlepin rule set.
 */

// defines mapping of product attributes to functions
// the format is: <function name>:<order number>:<attr1>:...:<attrn>, comma-separated ex.:
// func1:1:attr1:attr2:attr3, func2:2:attr3:attr4
function attribute_mappings() {
	return "virtualization_host:1:virtualization_host, " +
			"virtualization_host_platform:1:virtualization_host_platform, " +
			"architecture:1:architecture, " +
			"sockets:1:sockets, " +
			"requires_consumer_type:1:requires_consumer_type";
}

function pre_requires_consumer_type() {
	if (product.getAttribute("requires_consumer_type") != consumer.getType()) {
		pre.addError("rulefailed.consumer.type.mismatch");
	}
}

function pre_architecture() {
	if ((product.getAttribute("architecture") != "ALL") &&
			(!consumer.hasFact("cpu.architecture") ||
			(product.getAttribute("architecture") != consumer.getFact("cpu.architecture")))) {
		pre.addWarning("rulewarning.architecture.mismatch");
	}
}

function post_architecture() {
}

function pre_sockets() {
	if (!consumer.hasFact("cpu.cpu_socket(s)") ||
	    (parseInt(product.getAttribute("sockets")) < parseInt(consumer.getFact("cpu.cpu_socket(s)")))) {
		pre.addWarning("rulewarning.unsupported.number.of.sockets");
	}
}

function post_sockets() {
	
}

// Checks common for both virt host and virt platform entitlements:
function virtualization_common() {

// XXX: this check is bad, as we don't do virt based on consumer type anymore
	// Can only be given to a physical system:
//	if (consumer.getType() != "system") {
//		pre.addError("rulefailed.virt.ents.only.for.physical.systems");
//	}

	// Host must not have any guests currently (could be changed but for simplicities sake):
//	if (consumer.hasFact("total_guests") && parseInt(consumer.getFact("total_guests")) > 0) {
//		pre.addError("rulefailed.host.already.has.guests");
//	}
}

function pre_virtualization_host() {
	virtualization_common();
}

function post_virtualization_host() {
}


function pre_virtualization_host_platform() {
	virtualization_common();
}

function post_virtualization_host_platform() {
	// unlimited guests;
}

function pre_global() {
	if (consumer.hasEntitlement(product) && product.getAttribute("multi-entitlement") != "yes") {
		pre.addError("rulefailed.consumer.already.has.product");
	}

	// domain consumers can only consume products for domains
	if (consumer.getType() == "domain" && product.getAttribute("requires_consumer_type") != "domain") {
		pre.addError("rulefailed.consumer.type.mismatch");
	}
	
	// Support free entitlements for guests, if their parent has virt host or
	// platform,
	// and is entitled to the product the guest is requesting:
	if (consumer.getType() == "virt_system" && consumer.getParent() != null) {
		var parent = consumer.getParent();
		if ((parent.hasEntitlement("virtualization_host") || parent.hasEntitlement("virtualization_host_platform"))
				&& parent.hasEntitlement(product)) {
			pre.grantFreeEntitlement();
		}
	} else {
		pre.checkQuantity(pool);
	}
}

function post_global() {

}

function select_pool_global() {
    if (pools.size() > 0) {
	return pools.get(0);
    }

    return null;
}

