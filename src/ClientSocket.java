import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ClientSocket implements Runnable, IOCallback {
    private Object shared = null;
    static String data = null;

	static String send_data; 
    static JSONObject json_data;
    public ClientSocket(Object object) {
        shared = object;
    }
    
	public void run() {
        while (true) {
            synchronized (shared) {
                System.out.println(data);
                String split_data[] = data.split(",");
                String device_id = split_data[0];
                System.out.println(device_id);
                //final String code = split_data[4];
                //System.out.println(code);		
                
                String ipaddress = null;

                try {
                    URL whatismyip = new URL("http://checkip.amazonaws.com");
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            whatismyip.openStream()));

                    ipaddress = in.readLine(); //you get the IP as a String
                    System.out.println(ipaddress);
                } catch (IOException e){
                    e.printStackTrace();
                }
                
                Map<String, String> map = new HashMap<String, String>();
                map.put("type", "roku");
                map.put("id", device_id.trim());
                //map.put("code", code.trim());
                map.put("paired", "NULL");
                map.put("ipaddress", "" + ipaddress);
                map.put("foundWIFI", "NULL");

                JSONObject json = new JSONObject(map);
             // TODO Auto-generated method stub
            	SocketIO socket = null;
            	try {
            		//socket = new SocketIO("http://128.199.154.56:32769");
            		//socket = new SocketIO("http://52.8.2.219:3002");
            		//socket = new SocketIO("http://192.168.1.156:3020");
            		socket = new SocketIO("http://ec2-54-215-234-171.us-west-1.compute.amazonaws.com:3020");
            	} catch (MalformedURLException e1) {
            		// TODO Auto-generated catch block
            		e1.printStackTrace();
            	}
                socket.connect(new IOCallback() {

                    @Override          
                    public void onMessage(JSONObject json, IOAcknowledge ack) {
                        try {
                            System.out.println("Server said JSON:" + json.toString(2));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onMessage(String data, IOAcknowledge ack) {
                        System.out.println("Server said String: " + data);
                        
                    }

                    @Override
                    public void onError(SocketIOException socketIOException) {
                        System.out.println("an Error occured");
                        socketIOException.printStackTrace();
                    }

                    @Override
                    public void onDisconnect() {
                        System.out.println("Connection terminated.");
                    }

                    @Override
                    public void onConnect() {
                        System.out.println("Connection established");
                    }

                    @Override
                    public void on(String event, IOAcknowledge ack, Object... args) {
                        System.out.println("Server triggered event '" + event + "'");
                        System.out.println(event.toUpperCase());
                        json_data = (JSONObject) args[0];
                        System.out.println(json_data);
                        try {
                        	if(event.toUpperCase().equals("DEVICECONNECTED")){
                        		System.out.println("inside device connected");
                        		set_data(json_data);	
                        	}
                        	else if(event.toUpperCase().equals("TV")){
                        		System.out.println("inside on tv");
                        		try {
                                    if (json_data.getString("status").length() == 1){
                                    	System.out.println("length one");
                                    }
                                    else if (json_data.getString("status").length() == 2){
                                       set_paired_code(json_data);
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
                    
                   
                });

                // This line is cached until the connection is established.
                socket.emit("addDevice",json);

                  
            }
                try {
                    Thread.sleep(500); // only to view the sequence of execution
                   shared.notify();
                    shared.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();  
                }
            }
        }
    
	protected void set_paired_code(JSONObject json_data2) throws JSONException, IOException {
		// TODO Auto-generated method stub
		String status = json_data2.getString("status");
		status = "12";
		JavaWrapper.Send_status_to_roku(status);
	}

	public static void receive_data(String receive_data) {
		data = receive_data;
		System.out.println("Data " + data);
	}
	
	public static String send_data() {
		return send_data;
	}
	
	private void set_data(JSONObject json_data) throws IOException, JSONException {
		JavaWrapper.Send_code_to_roku(json_data);
	}

	@Override
	public void on(String arg0, IOAcknowledge arg1, Object... arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onConnect() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDisconnect() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onError(SocketIOException arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMessage(String arg0, IOAcknowledge arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMessage(JSONObject arg0, IOAcknowledge arg1) {
		// TODO Auto-generated method stub
		
	}
}

