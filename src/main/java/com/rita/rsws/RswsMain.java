package com.rita.rsws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rita.rsws.clients.SocketIoClient;

/**
 * the entry of the program
 * 
 * @author shaunyip@outlook.com
 *
 */
public class RswsMain {

	private static Logger verboseLogger = LoggerFactory
			.getLogger("verboseLogger");

	private static Logger logger = LoggerFactory.getLogger(RswsMain.class);

	public static void main(String[] args) {
		try {
			
			int rokuServerPort = Integer.parseInt(args[0]);
			String sioServerUrl = args[1];
			
			RokuServer rokuServer = new RokuServer(rokuServerPort);
			SocketIoClient sioClient = new SocketIoClient(sioServerUrl);
			rokuServer.setSioClient(sioClient);
			sioClient.setRokuServer(rokuServer);

			rokuServer.start();
			sioClient.start();

		} catch (Exception e) {
			String err = "System failed. will exit. ";
			verboseLogger.error(err + " err is " + e.getMessage());
			logger.error(err, e);
			System.exit(1);
		}
	}
}
