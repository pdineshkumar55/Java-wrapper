package com.rita.rsws.clients;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 
 * @author shaunyip@outlook.com
 *
 */
public class RswsSocketIoClient {

	private Object shared = null;
	static String data = null;

	static String send_data;
	static JSONObject json_data;

	public RswsSocketIoClient(Object object) {
		shared = object;
	}

	public void run() {

		IO io;
		while (true) {
			synchronized (shared) {
				System.out.println(data);
				String split_data[] = data.split(",");
				String device_id = split_data[0];
				System.out.println(device_id);
				// final String code = split_data[4];
				// System.out.println(code);

				String ipaddress = null;

				try {
					URL whatismyip = new URL("http://checkip.amazonaws.com");
					BufferedReader in = new BufferedReader(
							new InputStreamReader(whatismyip.openStream()));

					ipaddress = in.readLine(); // you get the IP as a String
					System.out.println(ipaddress);
				} catch (IOException e) {
					e.printStackTrace();
				}

				Map<String, String> map = new HashMap<String, String>();
				map.put("type", "roku");
				map.put("id", device_id.trim());
				// map.put("code", code.trim());
				map.put("paired", "NULL");
				map.put("ipaddress", "" + ipaddress);
				map.put("foundWIFI", "NULL");

				JSONObject json = new JSONObject(map);
				// TODO Auto-generated method stub
				String url = "http://localhost:11051";
				final Socket socket = createSocketFromUrl(url);

				socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

					@Override
					public void call(Object... args) {
						System.out.println("Connection established");
					}

				}).on("event", new Emitter.Listener() {

					@Override
					public void call(Object... args) {
						String event = "what is it?";

						System.out.println("Server triggered event '" + event
								+ "'");
						System.out.println(event.toUpperCase());
						json_data = (JSONObject) args[0];
						System.out.println(json_data);
						try {
							if (event.toUpperCase().equals("DEVICECONNECTED")) {
								System.out.println("inside device connected");
								set_data(socket, json_data);
							} else if (event.toUpperCase().equals("TV")) {
								System.out.println("inside on tv");
								try {
									if (json_data.getString("status").length() == 1) {
										System.out.println("length one");
									} else if (json_data.getString("status")
											.length() == 2) {
										set_paired_code(socket, json_data);
									}

								} catch (JSONException e) {
									return;
								}

							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}

				}).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

					@Override
					public void call(Object... args) {
						System.out.println("Connection terminated.");
					}

				});
				socket.connect();
			}

			// socket.emit("addDevice", json);

			try {
				Thread.sleep(500); // only to view the sequence of execution
				shared.notify();
				shared.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private Socket createSocketFromUrl(String url) {
		try {
			return IO.socket(url);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected void set_paired_code(Socket socket, JSONObject json_data2)
			throws JSONException, IOException {
		// TODO Auto-generated method stub
		String status = json_data2.getString("status");
		status = "12";
		socket.emit("addDevice", status);
	}

	public static void receive_data(String receive_data) {
		data = receive_data;
		System.out.println("Data " + data);
	}

	public static String send_data() {
		return send_data;
	}

	private void set_data(Socket socket, JSONObject json_data)
			throws IOException, JSONException {
		socket.emit("addDevice", json_data);
	}

}