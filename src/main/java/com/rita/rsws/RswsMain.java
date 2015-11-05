package com.rita.rsws;

import com.rita.rsws.clients.RswsSocketIoClient;

 
public class RswsMain {

	public static void main(String[] args) {
		RswsServers.getInstance().start(); 		
		new RswsSocketIoClient("sth").run();		
	}
}
