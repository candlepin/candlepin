package org.fedoraproject.candlepin.client.cmds;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.StringUtils;
import org.fedoraproject.candlepin.client.CandlepinConsumerClient;
import org.fedoraproject.candlepin.client.OperationResult;

public class UnSubscribeCommand extends BaseCommand {

	@Override
	public String getDescription() {
		return "unsubscribe the registered user from all or specific subscriptions.";
	}

	@Override
	public String getName() {
		return "unsubscribe";
	}
	
	@Override
	public Options getOptions() {
		Options opts = super.getOptions();
		opts.addOption("s", "serial", true, "Certificate serial to unsubscribe");
		return opts;
	}

	@Override
	public void execute(CommandLine cmdLine) {
		 CandlepinConsumerClient client = this.getClient();
		 if(!client.isRegistered()){
	        System.out.println("This system is currently not registered.");
	       	return;
		 }
		 String serial = cmdLine.getOptionValue("s");
		OperationResult result = StringUtils.isEmpty(serial) ? client
				.unBindAll() : client.unBindBySerialNumber(serial);
		switch(result){
			case NOT_A_FAILURE:
				System.out.println("Unsubscribed successfully");
				break;
			default:
				System.err.println("Unable to perform unsubscribe!");
		}
		
		client.updateEntitlementCertificates();
	}
}
