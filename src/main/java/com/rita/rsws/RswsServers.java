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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * The main program
 * 
 * @author shaunyip@outlook.com
 *
 */
public class RswsServers {

	private static RswsServers instance;

	private RswsServers() {
	}

	public static RswsServers getInstance() {
		if (null == instance) {
			instance = new RswsServers();
		}
		return instance;
	}

	private ServerSocket rokuServer;

	private static Logger verboseLogger = LoggerFactory
			.getLogger("verboseLogger");
	private static Logger logger = LoggerFactory.getLogger(RswsServers.class);

	private static final int ROKU_SERVER_PORT = 11050;

	private ExecutorService rokuSocketWorkers;

	private static final String CHARSET = "utf8";

	public ConcurrentHashMap<String, Socket> rokuSockets = new ConcurrentHashMap<String, Socket>();

	public Socket sioSocket;

	public void start() {

		verboseLogger.info("Wrap server initiating...");

		// initialize roku socket workers
		rokuSocketWorkers = Executors.newCachedThreadPool();

		try {
			startRokuServer();
		} catch (Exception e) {
			logger.error("Cannot start roku server socket", e);
			return;
		}

	}

	private void startRokuServer() throws Exception {
		// start up the server
		try {
			rokuServer = new ServerSocket(ROKU_SERVER_PORT);
			verboseLogger.info("Roku server socket started on port "
					+ ROKU_SERVER_PORT);
		} catch (IOException e) {
			throw e;
		}

		// take incoming connections in an unblocking way
		Runnable startServerTask = new Runnable() {
			public void run() {
				while (true) {
					Socket clientSocket = null;
					try {
						clientSocket = rokuServer.accept();
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

	public void writeMsgToRoku(String deviceId, String msg) throws IOException {

		Socket rokuSocket = rokuSockets.get(deviceId);
		if (rokuSocket == null || rokuSocket.isClosed()) {
			verboseLogger
					.error("msg from socket.io has to be discarded because there is no corresponding roku socket yet or the roku socket has been closed. socket.io socket = "
							+ sioSocket.getRemoteSocketAddress()
							+ " and msg = " + msg);
		}

		synchronized (rokuSocket) {
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

				JSONObject jsonObj = toJsonObj(rokuMsg);
				if (jsonObj == null) {
					verboseLogger
							.error("fail to parse the message from roku with socket = "
									+ socket.getRemoteSocketAddress()
									+ " and msg = " + rokuMsg);
					continue;
				}

				String deviceId = readStringFromJson(jsonObj, "id");
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
				String msgForSio = convertRokuMsgToSioMsg(jsonObj);
				if (sioSocket == null || sioSocket.isClosed()) {
					verboseLogger
							.error("msg from roku has to be discarded because there is no socket.io socket yet or the socket.io has been closed. roku socket = "
									+ socket.getRemoteSocketAddress()
									+ " and msg = " + rokuMsg);
					continue;
				}

				try {
					writeMsgToSio(msgForSio);
					String logEntry = MessageFormat
							.format("successfully output socket.io msg. rokuMsg = {0}, rokuClient = {1}, msgForSocketIo = {2}",
									rokuMsg, socket.getRemoteSocketAddress(),
									msgForSio);
					verboseLogger.info(logEntry);
				} catch (IOException e) {
					String errMsg = MessageFormat
							.format("failed to output msg to socket.io. rokuMsg = {0}, rokuClient = {1}, msgForSocketIo = {2}, exception = {3}",
									rokuMsg, socket.getRemoteSocketAddress(),
									msgForSio, e.getMessage());
					logger.error(errMsg, e);
					verboseLogger.error(errMsg);
					continue;
				}

			}

		}

	}

	private String convertRokuMsgToSioMsg(JSONObject jsonObj) {
		return jsonObj.toJSONString();
	}

	private String convertSioMsgToRokuMsg(JSONObject jsonObj) {
		return jsonObj.toJSONString();
	}

	/**
	 * will return null if the msg is invalid
	 * 
	 * @param msg
	 * @return
	 */
	private JSONObject toJsonObj(String msg) {
		try {
			return JSON.parseObject(msg);
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

	/**
	 * write msg to socket io
	 * 
	 * @throws IOException
	 */
	private void writeMsgToSio(String msg) throws IOException {
		synchronized (sioSocket) {
			sioSocket.getOutputStream().write(msg.getBytes(CHARSET));
		}

	}

}
