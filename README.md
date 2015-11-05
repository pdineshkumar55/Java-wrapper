<<<<<<< HEAD
## How to Develop and Deploy

0. Install Apache Maven (version >= 3) on your local dev machine. Set up the path as the tutorial tells.
1. Clone this code
2. Import it to Eclipse, with File => Import => Existing Maven Projects
3. Code Change: To Add New Event, you need to: 
   
    Update RokuServer.convertRokuMsgToSioMsg()
    
    Update SocketIoClient.start(), add another "on" statement

4. After changing code, go to the directory of this, and run "mvn clean package" (and push the changes of course)
5. You will found a "rsws-xxxxxxx-jarset.zip" file in "target" directory
6. Upload the file to the AWS server


7. unzip it, goto the "bin" directory, and run 

	 
		startup.sh port-for-wrapper-server socket-io.server-url 
	 

   Please check the console.  

100. p.s. You can also just find the "rsws-xxx.jar" file under "target" and replace the existing one on AWS. 

## Troubleshooting

The log can also be found in file verbose.log and file root.log
=======
# EverTV_Roku_Java_Wrapper
EverTV_Roku_Java_Wrapper Git for Avezatech to upload code.
AWS instance is 54.215.234.171. 
Permission certificate for instance is EverRokuDev.pem
>>>>>>> 2f15f87070d9d62162653a67b84bbc84dfd41f63
