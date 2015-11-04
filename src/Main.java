import java.io.IOException;

public class Main {
    public Main(){
           }
    public static void main(String[] args) throws IOException {
        Object shared = new Object();
        JavaWrapper evenRunnable = new JavaWrapper(shared);
        ClientSocket oddRunnable = new ClientSocket(shared);
        
        Thread evenThread = new Thread(evenRunnable, "evenThread");
        Thread oddThread = new Thread(oddRunnable, "oddThread");
        evenThread.start();
        oddThread.start();
    }
    
    
}
