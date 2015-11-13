package com.rita.rsws;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.json.JSONException;
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

		startRokuSocketsScavenger();

	}

	/**
	 * remove dead roku sockets from {@link #rokuSockets}
	 */
	private void startRokuSocketsScavenger() {
		new Timer().schedule(new RokuSocketsScavengerTask(), 60l * 1000,
				60l * 1000);
	}

	private class RokuSocketsScavengerTask extends TimerTask {

		@Override
		public void run() {
			verboseLogger
					.info("roku socket scavenger is working. Currently there are "
							+ rokuSockets.size()
							+ " deviceId-tagged socket objects. Some of them may have been closed");
			for (Iterator<Entry<String, Socket>> iter = rokuSockets.entrySet()
					.iterator(); iter.hasNext();) {
				Entry<String, Socket> entry = iter.next();
				Socket socket = entry.getValue();
				if (socket == null || socket.isClosed()) {
					iter.remove();
				}

			}
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

		BufferedReader reader = new BufferedReader(new InputStreamReader(in,
				CHARSET));
		String line = reader.readLine();
		return line;

	}

	public void sendMsgToRoku(String deviceId, String event, String rokuMsg)
			throws Exception {

		Socket rokuSocket = rokuSockets.get(deviceId);
		if (rokuSocket == null || rokuSocket.isClosed()) {
			throw new Exception(
					"msg from socket.io has to be discarded because there is no "
							+ "corresponding roku socket yet or the roku socket has been closed.  rokuMsg = "
							+ rokuMsg);
		}

		synchronized (rokuSocket) {
			rokuSocket.getOutputStream().write(rokuMsg.getBytes(CHARSET));
		}
		String logEntry = MessageFormat
				.format("successfully output msg to roku. rokuMsg = {0}, rokuClient = {1},  event={2}",
						rokuMsg, rokuSocket.getRemoteSocketAddress(), event);
		verboseLogger.info(logEntry);

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
				} catch (IOException e1) {
					String errMsg = "fail to read from roku with socket = "
							+ socket.getRemoteSocketAddress()
							+ ". Will not talk to this socket any more";
					verboseLogger.error(errMsg);
					logger.error(errMsg, e1);

					try {
						socket.close();
					} catch (IOException e) {
						// ignore it
					}
					return;
				}

				try {
					rokuMsg = readMsgFromSocket(in);
					if (rokuMsg == null) { // the client has been lost
						socket.close();
						return;
					}
					// if (rokuMsg.isEmpty()) {
					// continue;
					// }
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

				Object[] sioMsgResult = null;
				String event = null;
				JSONObject sioMsgJsonObj = null;

				try {
					// do output
					sioMsgResult = convertRokuMsgToSioMsg(rokuMsg);
					event = (String) sioMsgResult[0];
					sioMsgJsonObj = (JSONObject) sioMsgResult[1];

					String deviceId = sioMsgJsonObj.getString("id");
					if (deviceId == null) {
						verboseLogger
								.error("fail to extract device_id from roku with socket = "
										+ socket.getRemoteSocketAddress()
										+ " and msg = " + rokuMsg);
						continue;
					}

					// associate the socket with its deviceId

					rokuSockets.put(deviceId, socket);

					sioClient.sendMsgToSio(event, sioMsgJsonObj);
					String logEntry = MessageFormat
							.format("successfully output msg to socket.io server. rokuMsg = {0}, rokuClient = {1},  event={2}, jsonForSocketIo = {3}",
									rokuMsg, socket.getRemoteSocketAddress(),
									event, sioMsgJsonObj);
					verboseLogger.info(logEntry);
				} catch (Exception e) {
					String errMsg = MessageFormat
							.format("failed to output msg to socket.io server. rokuMsg = {0}, rokuClient = {1}, event= {2}, jsonForSocketIo = {3}, exception = {4}",
									rokuMsg, socket.getRemoteSocketAddress(),
									event, sioMsgJsonObj, e.getMessage());
					logger.error(errMsg, e);
					verboseLogger.error(errMsg);
					continue;
				}

			}

		}

	}

	/**
	 * 
	 * @param rokuMsg
	 * @return the 1st is the event, the 2nd is the data
	 * @throws JSONException
	 */
	private Object[] convertRokuMsgToSioMsg(String rokuMsg)
			throws JSONException {
		String[] segs = StringUtils.split(rokuMsg, ",");
		if (segs == null || segs.length == 0) {
			throw new IllegalArgumentException(
					"the msg from roku seems empty. It is " + rokuMsg);
		}

		Object[] result = new Object[2];
		JSONObject sioMsgJsonObj = new JSONObject();
		result[1] = sioMsgJsonObj;

		if (segs[0].equalsIgnoreCase(SocketIoClient.ADD_DEVICE)) {

			result[0] = SocketIoClient.ADD_DEVICE;

			if (segs.length >= 2) {
				sioMsgJsonObj.put("id", segs[1]);
			}
			if (segs.length >= 3) {
				sioMsgJsonObj.put("paired", segs[2]);
			}
			if (segs.length >= 3) {
				sioMsgJsonObj.put("foundWIFi", segs[3]);
			}
			if (segs.length >= 4) {
				sioMsgJsonObj.put("type", segs[4]);
			}
			if (segs.length >= 5) {
				sioMsgJsonObj.put("code", segs[5]);
			}
			if (segs.length >= 6) {
				sioMsgJsonObj.put("ip_adddress", segs[6]);
			}
		} else if (segs[0].equalsIgnoreCase(SocketIoClient.PAUSE)) {

			result[0] = SocketIoClient.PAUSE;

			if (segs.length >= 2) {
				sioMsgJsonObj.put("id", segs[1]);
			}
			if (segs.length >= 3) {
				sioMsgJsonObj.put("video", segs[2]);
			}
			if (segs.length >= 4) {
				sioMsgJsonObj.put("timestamp", segs[3]);
			}
		} else if (segs[0].equalsIgnoreCase(SocketIoClient.PLAY)) {

			result[0] = SocketIoClient.PLAY;

			if (segs.length >= 2) {
				sioMsgJsonObj.put("id", segs[1]);
			}
			if (segs.length >= 3) {
				sioMsgJsonObj.put("video", segs[2]);
			}
			if (segs.length >= 4) {
				sioMsgJsonObj.put("timestamp", segs[3]);
			}
		}

		return result;
	}

	public void setSioClient(SocketIoClient sioClient) {
		this.sioClient = sioClient;
	}
}
