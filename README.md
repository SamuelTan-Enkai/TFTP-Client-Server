# TFTP Client & Server

Provides a simplified version of the Trivial File Transfer Protocol (TFTP) using UDP sockets using java programming. The client and server programs are designed to transfer files reliably over a network.

### TftpClient.java
The client sends a RRQ packet to the server with the name of the file to be transferred. It then listens for DATA packets from the server and sends ACK packets to acknowledge each packet. If a packet is lost, the client retransmits the ACK packet.

### TftpServer.java
The server listen for RRQ packets from clients and responds with a DATA packet containing the requested file. It uses seprate threads to send each file, allowing multiple clients to be served simultaneously. If a packet if lost, the server restransmits the packet.

### Packet Formats:
#### RRQ Packet
- 1 byte: Type (RRQ = 1)
- Variable bytes: Filename

#### DATA Packet
- 1 byte: Type (DATA = 2)
- 1 byte: Block number
- Variable bytes: Data

#### ACK Packet
- 1 byte: Type (ACK = 3)
- 1 byte: Block number

#### ERROR Packet
- 1 byte: Type (ERROR = 4)
- Variable bytes: Error message
