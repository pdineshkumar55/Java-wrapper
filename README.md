## How to Setup
Please follow the sequence when starting up:
 
1. Start Java Wrapper server

2. Let the Socket.IO Client make a connection to the Java Wrapper Server, but don't send any message to Roku

3. Then Roku clients can make connections and clients from the two sides can can send message at will


## How to unit test the Wrapper Server

1. Run it 

2. "telnet server 11051" as sockiet.io client

3. "telnet server 11050" as Roku client A

3. "telnet server 11050" as Roku client B

Now send messages via telnet for fun!  Check the printed messaged on console. 

### Test Cases

1. socket.io client sends invalid text
2. socket.io client sends valid text without a device id
3. socket.io client sends valid text with a device_id that has not connected yet
4. socket.io client sends valid text with a device_id which has connected to the server
5. socket.io client sends valid text with a device_id which has connected to the server but has just broken connection

1. roku client sends invalid text
2. roku client sends valid text without a device id
3. roku client sends valid text with its device_id

Check the printed messaged on console. 