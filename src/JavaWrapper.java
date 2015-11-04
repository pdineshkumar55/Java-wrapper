import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.json.JSONException;
import org.json.JSONObject;

public class JavaWrapper implements Runnable {
    private Object shared = null;
    private static ServerSocket serverSocket;
    String s;
    public static Socket ss_server = null;
    
    public JavaWrapper(Object object) throws IOException {
    	serverSocket = new ServerSocket(3010);
        shared = object;
    }
    public void run() {
        while (true) {
            synchronized (shared) {
      
                   System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "...");
                   
				try {
					ss_server = serverSocket.accept();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
                   System.out.println("Just connected to " + ss_server.getRemoteSocketAddress());
     
                try {
                	DataInputStream in = new DataInputStream(new BufferedInputStream(ss_server.getInputStream()));
                    byte[] bytes = new byte[1024];
                    in.read(bytes);
                    s = new String(bytes);
                    System.out.println(s);
                    ClientSocket.receive_data(s);
                    //serverSocket.close();

                    Thread.sleep(500); //only to view sequence of execution
                    shared.notify();
                    shared.wait();
                    
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        }
    }
    
    public static void Send_code_to_roku(JSONObject roku_data) throws IOException, JSONException{
    	System.out.println("roku_data" + roku_data);
    	System.out.println(ss_server.isConnected());
    	ss_server.setKeepAlive(true);
    	DataOutputStream out = new DataOutputStream(ss_server.getOutputStream());
    	//String output = roku_data;
    	System.out.println(ss_server.isClosed());
    	String device_id = null, code = null,paired = null;
		
			try {
				device_id = roku_data.getString("id");
				//code = roku_data.getString("code");
		    	paired = roku_data.getString("paired");
		    	System.out.println(paired);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
			String output = device_id + ","+ code +"," + paired;
	    	byte[] out_byte = output.getBytes();
	        try{
	        out.write(out_byte, 0, out_byte.length);
	        //out.writeUTF(output);
	        }
	        catch(Exception exc){
	                System.out.println(exc);
	        }
	        //ss_server.close();
    	
    }
    
	public Object send_data() {
		return s;
	}
	
	public static void Send_status_to_roku(String status) throws IOException {
    	DataOutputStream out = new DataOutputStream(ss_server.getOutputStream());
			
			String output = status;
	    	byte[] out_byte = output.getBytes();
	        try{
	        out.write(out_byte, 0, out_byte.length);
	        }
	        catch(Exception exc){
	                System.out.println(exc);
	        }
	        //ss_server.close();
	}
	
}


