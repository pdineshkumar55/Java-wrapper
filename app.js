var server = require('http').Server();
var io = require('socket.io').listen(server);
io.on('connection', function(socket){
console.log("conection on")
    socket.on('message', function(data){
    console.log("Received message : "+data);
    data.paired = "true"
    socket.emit('message',data);
  });
  socket.on('disconnect', function(){
    // client disconnected
   });
});
server.listen(3020);

