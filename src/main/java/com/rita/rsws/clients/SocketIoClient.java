package com.rita.rsws.clients;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.net.URISyntaxException;
import java.text.MessageFormat;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rita.rsws.RokuServer;

/**
 * 
 * @author shaunyip@outlook.com
 *
 */
public class SocketIoClient {

	private static final String STATUS = "status";

 

	private RokuServer rokuServer;

	private Socket socket;

	private String sioServerUrl;
	
	

	private static Logger verboseLogger = LoggerFactory
			.getLogger("verboseLogger");

	private static Logger logger = LoggerFactory
			.getLogger(SocketIoClient.class);

 
 
	public SocketIoClient(String sioServerUrl) {
		this.sioServerUrl = sioServerUrl;
	}

	public void start() {
		socket = connectSocketIoServer();
		socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

			@Override
			public void call(Object... args) {
				verboseLogger
						.info("Connection with socket.io is done. the server is "
								+ sioServerUrl);
			}

		}).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				verboseLogger
						.info("Connection with socket.io is broken. the server is "
								+ sioServerUrl);
			}

		}).on(STATUS, new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				JSONObject jsonObj = getJsonDataFromEventAndLog(STATUS, args);
				String deviceId = getDeviceIdAndLog(jsonObj);
				if (deviceId == null) {
					return;
				}
				try {
					rokuServer.sendMsgToRoku(deviceId, jsonObj);
				} catch (Exception e) {
					logSendToRokuException(jsonObj, e);

				}
			}

		});
		socket.connect();
	}

	private void logSendToRokuException(JSONObject jsonObj, Exception e) {
		String err = MessageFormat.format(
				"fail to send msg to roku because {0}, msg = {1} ",
				e.getMessage(), jsonObj.toString());
		verboseLogger.error(err);
		logger.error(err, e);
	}

	private JSONObject getJsonDataFromEventAndLog(String event, Object... args) {
		JSONObject jsonObj = (JSONObject) args[0];
		verboseLogger.info("On event = {}, json = {} ", event,
				jsonObj.toString());
		return jsonObj;
	}

	private String getDeviceIdAndLog(JSONObject jsonObj) {
		String deviceId = null;
		try {
			deviceId = jsonObj.getString("id");
		} catch (JSONException e) {
			deviceId = null;
		}

		if (deviceId == null) {
			verboseLogger
					.error("msg from socket.io has to be discarded because the deviceId in the message is null or the message cannot be parsed"
							+ ". The msg = " + jsonObj.toString());
		}
		return deviceId;

	}

	private Socket connectSocketIoServer() {

		try {
			return IO.socket(sioServerUrl);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

 

	public void sendMsgToSio(String event, JSONObject jsonObj) {
		socket.emit(event, jsonObj);
		
	}
	
	public void setRokuServer(RokuServer rokuServer) {
		this.rokuServer = rokuServer;
	}

}