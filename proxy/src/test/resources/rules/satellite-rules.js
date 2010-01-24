function virtualization_host() {
	if (parseInt(consumer.getFact("total_guests")) > 0) {
		result.addError("rulefailed.host.already.has.guests");
	}
}