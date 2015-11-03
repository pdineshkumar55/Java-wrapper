var server = require('http').Server();
// var io = require('socket.io').listen(server);

// var thisServer, remoteServer


// io.on('connection', function(socket){
// 	thisServer = socket
// console.log("conection on")
//     socket.on('message', function(data){
//     console.log("Received message : "+data);
//     data.paired = "true"
//     socket.emit('message',data);
//   });
//   socket.on('disconnect', function(){
//     // client disconnected
//    });
// });


// var realServer = require('socket.io').listen('http://52.8.2.219:3002');
// // realServer.connect();

// realServer.on('connection', function(socket){
// 	remoteServer = socket
// 	console.log('realServer on')
// })




// Server 1
var io = require('socket.io').listen(server); // This is the Server for SERVER 1
var other_server = require("socket.io-client")('http://52.8.2.219:3010'); // This is a client connecting to the SERVER 2

other_server.on("connect",function(){
    other_server.on('message',function(data){
        // We received a message from Server 2
        // We are going to forward/broadcast that message to the "Lobby" room
        io.to('lobby').emit('message',data);
    });
});

io.sockets.on("connection",function(socket){
    // Display a connected message
    console.log("User-Client Connected!");

    // Lets force this connection into the lobby room.
    socket.join('lobby');

    // Some roster/user management logic to track them
    // This would be upto you to add :)

    // When we receive a message...
    socket.on("message",function(data){
        // We need to just forward this message to our other guy
        // We are literally just forwarding the whole data packet
        other_server.emit("message",data);
    });

    socket.on("disconnect",function(data){
        // We need to notify Server 2 that the client has disconnected
        other_server.emit("message","UD,"+socket.id);

        // Other logic you may or may not want
        // Your other disconnect code here
    });
});







server.listen(3020);

