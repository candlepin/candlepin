/*
 * Default Candlepin rule set.
 */

// defines mapping of product attributes to functions
// the format is: <function name>:<order number>:<attr1>:...:<attrn>, comma-separated ex.:
// func1:1:attr1:attr2:attr3, func2:2:attr3:attr4
function attribute_mappings() {
	return "";
}

// Checks common for both virt host and virt platform entitlements:
function virtualization_common() {
    pre_global();

	// Can only be given to a physical system:
	if (consumer.getType() != "system") {
		pre.addError("rulefailed.virt.ents.only.for.physical.systems");
	}

	// Host must not have any guests currently (could be changed but for simplicities sake):
//	if (consumer.hasFact("total_guests") && parseInt(consumer.getFact("total_guests")) > 0) {
//		pre.addError("rulefailed.host.already.has.guests");
//	}
}

function pre_virtualization_host() {
	virtualization_common();
}

function post_virtualization_host() {
	post_global();
}


function pre_virtualization_host_platform() {
	virtualization_common();
}

function post_virtualization_host_platform() {
	// unlimited guests:
	post_global();
}

function pre_global() {
	if (consumer.hasEntitlement(product) && product.getAttribute("multi-entitlement") != "yes") {
		pre.addError("rulefailed.consumer.already.has.product");
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
	return pools.getFirst();
    }

    return null;
}

