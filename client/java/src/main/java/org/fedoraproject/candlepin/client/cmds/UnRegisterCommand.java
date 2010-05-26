package org.fedoraproject.candlepin.client.cmds;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.fedoraproject.candlepin.client.CandlepinConsumerClient;

public class UnRegisterCommand extends BaseCommand {

	@Override
	public String getDescription() {
		return "unregister";
	}

	@Override
	public String getName() {
		return "unregister";
	}
	
	@Override
	public void execute(CommandLine cmdLine) {
		 CandlepinConsumerClient client = this.getClient();
		 if(client != null && client.isRegistered()){
			 client.unRegister();
		 }else{
			 System.out.println("This system is currently not registered.");
		 }
	}
	
	@Override
	public Options getOptions() {
		Options opts = super.getOptions();
		opts.addOption("debug", true, "debug level"); //flag ignored.
		return opts;
	}

}
