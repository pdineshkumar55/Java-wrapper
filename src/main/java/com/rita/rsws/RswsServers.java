package com.rita.rsws;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.IOUtils;
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

	private ServerSocket rokuServer;
	private ServerSocket sioServer;

	private static Logger verboseLogger = LoggerFactory
			.getLogger("verboseLogger");
	private static Logger logger = LoggerFactory.getLogger(RswsServers.class);

	private static final int ROKU_SERVER_PORT = 11050;
	private static final int SIO_SERVER_PORT = 11051;

	private ExecutorService rokuSocketWorkers;

	private static final String CHARSET = "utf8";

	private ConcurrentHashMap<String, Socket> rokuSockets = new ConcurrentHashMap<String, Socket>();

	private Socket sioSocket;

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

		try {
			startSioServer();
		} catch (Exception e) {
			logger.error("Cannot start socket.io server socket", e);
			return;
		}

	}

	private void startSioServer() throws IOException {

		// start up the server
		try {
			sioServer = new ServerSocket(SIO_SERVER_PORT);
			verboseLogger.info("socket.io server socket started on port "
					+ SIO_SERVER_PORT);
		} catch (IOException e) {
			throw e;
		}

		// take incoming connections in an unblocking way
		Runnable startServerTask = new Runnable() {
			public void run() {
				while (true) {

					try {
						sioSocket = sioServer.accept();
					} catch (IOException e) {
						logger.error(
								"Failed to take an incoming connection from socket.io.",
								e);
						verboseLogger
								.error("Failed to take an incoming connection from socket.io."
										+ e.getMessage());
						continue;
					}
					verboseLogger
							.info("A new connection from socket.io has been set up. "
									+ sioSocket.getRemoteSocketAddress());

					rokuSocketWorkers.submit(new ReadSioSocketTask());

				}

			}
		};

		Executors.newSingleThreadExecutor().submit(startServerTask);

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

	private class ReadSioSocketTask implements Runnable {

		// this is thread-safe because only one thread will be used to read the
		// socket.io socket
		@Override
		public void run() {
			while (true) {

				String sioMsg = null;
				InputStream in = null;
				try {
					in = sioSocket.getInputStream();
					sioMsg = IOUtils.toString(in, CHARSET);
					verboseLogger.info("msg got from socket.io with socket = "
							+ sioSocket.getRemoteSocketAddress()
							+ " and msg = " + sioMsg);

				} catch (IOException e) {
					logger.error("fail to read from socket.io with socket = "
							+ sioSocket.getRemoteSocketAddress(), e);
					verboseLogger
							.error("fail to read from socket.io with socket = "
									+ sioSocket.getRemoteSocketAddress());
					continue;
				}

				JSONObject jsonObj = toJsonObj(sioMsg);
				if (jsonObj == null) {
					verboseLogger
							.error("fail to parse the message from socket.io with socket = "
									+ sioSocket.getRemoteSocketAddress()
									+ " and msg = " + sioMsg);
					continue;
				}

				String deviceId = readStringFromJson(jsonObj, "id");
				if (deviceId == null) {
					verboseLogger
							.error("fail to extract device_id from socket.io msg with socket = "
									+ sioSocket.getRemoteSocketAddress()
									+ " and msg = " + sioMsg);
					continue;
				}

				Socket rokuSocket = rokuSockets.get(deviceId);
				if (rokuSocket == null || rokuSocket.isClosed()) {
					verboseLogger
							.error("msg from socket.io has to be discarded because there is no corresponding roku socket yet or the roku socket has been closed. socket.io socket = "
									+ sioSocket.getRemoteSocketAddress()
									+ " and msg = " + sioMsg);
				}

				// do output
				String msgForRoku = convertRokuMsgToSioMsg(jsonObj);

				try {
					writeMsgToRoku(rokuSocket, msgForRoku);
				} catch (IOException e) {
					String errMsg = MessageFormat
							.format("failed to output roku msg. sioMsg = {0}, socket.io socket = {1},  roku socket = {2}, msgForRoku = {3}",
									sioMsg, sioSocket.getRemoteSocketAddress(),
									rokuSocket.getRemoteSocketAddress(),
									msgForRoku);
					logger.error(errMsg, e);
					verboseLogger.error(errMsg);
				}

			}

		}
	}

	private void writeMsgToRoku(Socket rokuSocket, String msg)
			throws IOException {

		synchronized (sioSocket) {
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
					rokuMsg = IOUtils.toString(in, CHARSET);
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
				}

				try {
					writeMsgToSio(msgForSio);
				} catch (IOException e) {
					String errMsg = MessageFormat
							.format("failed to output socket.io msg. rokuMsg = {0}, rokuClient = {1}, msgForSocketIo = {2}",
									rokuMsg, socket.getRemoteSocketAddress(),
									msgForSio);
					logger.error(errMsg, e);
					verboseLogger.error(errMsg);
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
