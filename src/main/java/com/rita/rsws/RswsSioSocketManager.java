package com.rita.rsws;

/**
 * manager for socket.io sockets
 * 
 * @author shaunyip@outlook.com
 *
 */
public class RswsSioSocketManager {

	private static RswsSioSocketManager instance;

	private RswsSioSocketManager() {
	}

	public static RswsSioSocketManager getInstance() {
		if (null == instance) {
			instance = new RswsSioSocketManager();
		}
		return instance;
	}

}
