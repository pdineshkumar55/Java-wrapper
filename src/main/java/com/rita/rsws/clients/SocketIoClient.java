package com.rita.rsws.clients;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
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

	public static final String STATUS = "status";
	public static final String ADD_DEVICE = "addDevice";

	public static final String DEVICE_CONNECTED = "deviceConnected";
	public static final String TV = "tv";
	public static final String CAPTURE = "capture";
	public static final String PAUSE = "pause";
	public static final String PLAY = "play";

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
					rokuServer.sendMsgToRoku(deviceId, STATUS, null);
				} catch (Exception e) {
					logSendToRokuException(jsonObj, e);

				}
			}

		}).on(DEVICE_CONNECTED, new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				JSONObject jsonObj = getJsonDataFromEventAndLog(
						DEVICE_CONNECTED, args);
				String deviceId = getDeviceIdAndLog(jsonObj);
				if (deviceId == null) {
					return;
				}

				try {
					List<String> rokuSegs = new ArrayList<String>();
					rokuSegs.add(DEVICE_CONNECTED);
					rokuSegs.add(jsonObj.getString("id"));
					rokuSegs.add(jsonObj.getString("paired"));
					rokuSegs.add(jsonObj.getString("foundWIFi"));
					rokuSegs.add(jsonObj.getString("type"));
					rokuSegs.add(jsonObj.getString("code"));
					rokuSegs.add(jsonObj.getString("ip_adddress"));
					String rekoMsgs = joinRokuSegs(rokuSegs);
					rokuServer.sendMsgToRoku(deviceId, DEVICE_CONNECTED, rekoMsgs);
				} catch (Exception e) {
					logSendToRokuException(jsonObj, e);

				}
			}

		}).on(CAPTURE, new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				JSONObject jsonObj = getJsonDataFromEventAndLog(CAPTURE, args);
				String deviceId = null;
				try {
					deviceId = jsonObj.getString("rokuID");
				} catch (JSONException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				String Status = null;
				try {
					Status = jsonObj.getString("status");
				} catch (JSONException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				if (deviceId == null) {
					return;
				}
				try {
					rokuServer.sendMsgToRoku(deviceId, CAPTURE, "capture");
				} catch (Exception e) {
					logSendToRokuException(jsonObj, e);

				}
			}

		}).on(TV, new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				JSONObject jsonObj = getJsonDataFromEventAndLog(TV, args);
				String deviceId = getDeviceIdAndLog(jsonObj);
				String Status = null;
				try {
					Status = jsonObj.getString("status");
				} catch (JSONException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				if (deviceId == null) {
					return;
				}
				try {
					rokuServer.sendMsgToRoku(deviceId, TV, Status);
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
		verboseLogger.info("sockiet.io client on event = {}, json = {} ",
				event, jsonObj.toString());
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

	private String joinRokuSegs(List<String> rokuSegs) {
		return StringUtils.join(rokuSegs, ",") + "\r\n";
	}

	public void sendMsgToSio(String event, JSONObject jsonObj) {
		socket.emit(event, jsonObj);
	}

	public void setRokuServer(RokuServer rokuServer) {
		this.rokuServer = rokuServer;
	}

}
