package com.rita.rsws;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rita.rsws.clients.SocketIoClient;

/**
 * The main program
 * 
 * @author shaunyip@outlook.com
 *
 */
public class RokuServer {

	private ServerSocket rokuServerSocket;

	private static Logger verboseLogger = LoggerFactory
			.getLogger("verboseLogger");
	private static Logger logger = LoggerFactory.getLogger(RokuServer.class);

	private int rokuServerPort;

	private ExecutorService rokuSocketWorkers;

	private static final String CHARSET = "utf8";

	private ConcurrentHashMap<String, Socket> rokuSockets = new ConcurrentHashMap<String, Socket>();

	private SocketIoClient sioClient;

	public RokuServer(int rokuServerPort) {
		this.rokuServerPort = rokuServerPort;
	}

	public void start() {

		verboseLogger.info("Wrap server initiating...");

		// initialize roku socket workers
		rokuSocketWorkers = Executors.newCachedThreadPool();

		try {
			startRokuServerSocket();
		} catch (Exception e) {
			logger.error("Cannot start roku server socket", e);
			return;
		}

	}

	private void startRokuServerSocket() throws Exception {
		// start up the server
		try {
			rokuServerSocket = new ServerSocket(rokuServerPort);
			verboseLogger.info("Roku server socket started on port "
					+ rokuServerPort);
		} catch (IOException e) {
			throw e;
		}

		// take incoming connections in an unblocking way
		Runnable startServerTask = new Runnable() {
			public void run() {
				while (true) {
					Socket clientSocket = null;
					try {
						clientSocket = rokuServerSocket.accept();
					} catch (IOException e) {
						logger.error(
								"Failed to take an incoming connection from Roku.",
								e);
						verboseLogger
								.error("Failed to take an incoming connection from Roku."
										+ e.getMessage());
						continue;
					}
					verboseLogger
							.info("A new connection from Roku has been set up. "
									+ clientSocket.getRemoteSocketAddress());

					ReadRokuSocketTask readRokuSocketTask = new ReadRokuSocketTask(
							clientSocket);
					rokuSocketWorkers.submit(readRokuSocketTask);

				}

			}
		};

		Executors.newSingleThreadExecutor().submit(startServerTask);

	}

	private String readMsgFromSocket(InputStream in) throws IOException {

		List<String> lines = new ArrayList<String>();

		while (true) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					in, CHARSET));
			String line = reader.readLine();
			if (line == null || line.isEmpty()) {
				return StringUtils.join(lines, "\n");
			}
			lines.add(line);
		}

	}

	public void sendMsgToRoku(String deviceId, JSONObject jsonObj)
			throws Exception {

		Socket rokuSocket = rokuSockets.get(deviceId);
		if (rokuSocket == null || rokuSocket.isClosed()) {
			throw new Exception(
					"msg from socket.io has to be discarded because there is no "
							+ "corresponding roku socket yet or the roku socket has been closed.  msg = "
							+ jsonObj.toString());
		}

		synchronized (rokuSocket) {
			String msg = convertSioMsgToRokuMsg(jsonObj);
			rokuSocket.getOutputStream().write(msg.getBytes(CHARSET));
		}

	}

	private class ReadRokuSocketTask implements Runnable {

		private Socket socket;

		public ReadRokuSocketTask(Socket socket) {
			this.socket = socket;
		}

		// this is thread-safe because only one thread will be used to read a
		// roku socket
		@Override
		public void run() {
			while (true) {

				String rokuMsg = null;
				InputStream in = null;
				try {
					in = socket.getInputStream();
					rokuMsg = readMsgFromSocket(in);
					if (rokuMsg == null || rokuMsg.isEmpty()) {
						continue;
					}
					verboseLogger.info("msg got from roku with socket = "
							+ socket.getRemoteSocketAddress() + " and msg = "
							+ rokuMsg);

				} catch (IOException e) {
					logger.error("fail to read from roku with socket = "
							+ socket.getRemoteSocketAddress(), e);
					verboseLogger.error("fail to read from roku with socket = "
							+ socket.getRemoteSocketAddress());
					continue;
				}

				JSONObject rokuJsonObj = toJsonObj(rokuMsg);
				if (rokuJsonObj == null) {
					verboseLogger
							.error("fail to parse the message from roku with socket = "
									+ socket.getRemoteSocketAddress()
									+ " and msg = " + rokuMsg);
					continue;
				}

				String deviceId = readStringFromJson(rokuJsonObj, "id");
				if (deviceId == null) {
					verboseLogger
							.error("fail to extract device_id from roku with socket = "
									+ socket.getRemoteSocketAddress()
									+ " and msg = " + rokuMsg);
					continue;
				}

				// associate the socket with its deviceId
				if (!rokuSockets.containsKey(deviceId)) {
					rokuSockets.put(deviceId, socket);
				}

				// do output
				JSONObject sioMsgJsonObj = convertRokuMsgToSioMsg(rokuJsonObj);
				try {
					sioClient.sendMsgToSio("event", sioMsgJsonObj);
					String logEntry = MessageFormat
							.format("successfully output socket.io msg. rokuMsg = {0}, rokuClient = {1}, msgForSocketIo = {2}",
									rokuMsg, socket.getRemoteSocketAddress(),
									sioMsgJsonObj);
					verboseLogger.info(logEntry);
				} catch (Exception e) {
					String errMsg = MessageFormat
							.format("failed to output msg to socket.io. rokuMsg = {0}, rokuClient = {1}, msgForSocketIo = {2}, exception = {3}",
									rokuMsg, socket.getRemoteSocketAddress(),
									sioMsgJsonObj, e.getMessage());
					logger.error(errMsg, e);
					verboseLogger.error(errMsg);
					continue;
				}

			}

		}

	}

	private JSONObject convertRokuMsgToSioMsg(JSONObject jsonObj) {
		return jsonObj;
	}

	private String convertSioMsgToRokuMsg(JSONObject jsonObj) {
		return jsonObj.toString();
	}

	/**
	 * will return null if the msg is invalid
	 * 
	 * @param msg
	 * @return
	 */
	private JSONObject toJsonObj(String msg) {
		try {
			return new JSONObject(msg);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * will return null if the msg is invalid
	 * 
	 * @return
	 */
	private String readStringFromJson(JSONObject jsonObj, String key) {
		try {
			return (String) jsonObj.getString(key);
		} catch (Exception e) {
			return null;
		}

	}

	public void setSioClient(SocketIoClient sioClient) {
		this.sioClient = sioClient;
	}
}
