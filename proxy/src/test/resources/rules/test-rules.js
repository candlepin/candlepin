// Checks common for both virt host and virt platform entitlements:
function virtualization_common() {
	pre.checkQuantity(pool);

	// Can only be given to a physical system:
	if (consumer.getType() != "system") {
		pre.addError("rulefailed.virt.ents.only.for.physical.systems");
		return;
	}

	// Host must not have any guests currently (could be changed but for simplicities sake):
	if (parseInt(consumer.getFact("total_guests")) > 0) {
		pre.addError("rulefailed.host.already.has.guests");
	}
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

function pre_CPULIMITED001() {
	pre.checkQuantity(pool);
	cpus = parseInt(consumer.getFact("cpu_cores"));
	if (cpus > 2) {
		pre.addError("rulefailed.too.many.cpu.cores");
	}
}

// Select pool for mythical product, based on which has the farthest expiry date:
function select_pool_LONGEST001() {
	var furthest = null;
	var iter = pools.iterator();
	while (iter.hasNext()) {
		var p = iter.next();
		if ((furthest == null) || (p.getEndDate() > furthest.getEndDate())) {
			furthest = p;
		}
	}
	return furthest;
}

function select_pool_QUANTITY001() {
	var highestMax = null;
	var iter = pools.iterator();
	while (iter.hasNext()) {
		var p = iter.next();
		if ((highestMax == null) || (p.getMaxMembers() > highestMax.getMaxMembers())) {
			highestMax = p;
		}
	}
	return highestMax;
}

// Bad rule! Just return null instead of picking a pool like it should:
function select_pool_BADRULE001() {
	return null;
}

function pre_global() {

	// Support free entitlements for guests, if their parent has virt host or platform,
	// and is entitled to the product the guest is requesting:
	if (consumer.getType() == "virt_system" && consumer.getParent() != null) {
		if ((consumer.getParent().hasEntitlement("virtualization_host") ||
				consumer.getParent().hasEntitlement("virtualization_host_platform")) &&
				consumer.getParent().hasEntitlement(product.getLabel())) {
			pre.grantFreeEntitlement();
		}

	}
	else {
		pre.checkQuantity(pool);
	}
}

function post_global() {

}
