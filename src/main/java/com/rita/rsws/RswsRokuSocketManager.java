package com.rita.rsws;

/**
 * manager for roku sockets.  No time to refine yet
 * 
 * @author shaunyip@outlook.com
 *
 */
public class RswsRokuSocketManager {

	private static RswsRokuSocketManager instance;

	private RswsRokuSocketManager() {
	}

	public static RswsRokuSocketManager getInstance() {
		if (null == instance) {
			instance = new RswsRokuSocketManager();
		}
		return instance;
	}

}
