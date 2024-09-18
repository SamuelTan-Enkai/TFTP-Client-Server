import java.net.*;
import java.io.*;

class TftpServer {

    /**
     * entry point into prgram
     * 
     * @param args
     */
    public static void main(String[] args) {
        TftpServer server = new TftpServer();
        server.start_server();
    }

    /**
     * start server opens a socket on port 51234 and waits for a request from a
     * client,starting a new thread to process request
     */
    public void start_server() {
        try {
            DatagramSocket ds = new DatagramSocket(50000);
            System.out.println("TftpServer on port " + ds.getLocalPort());

            while (true) {
                byte[] buf = new byte[1472];
                DatagramPacket dp = new DatagramPacket(buf, 1472);

                // receive a request from client
                ds.receive(dp);

                // create and start new thread
                TftpServerWorker worker = new TftpServerWorker(dp);
                worker.start();

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class TftpServerWorker extends Thread {
    private DatagramPacket req;
    private static final byte RRQ = 1;
    private static final byte DATA = 2;
    // private static final byte ACK = 3;
    private static final byte ERROR = 4;

    // constructor
    public TftpServerWorker(DatagramPacket req) {
        this.req = req;
    }

    /**
     * checks if request is an rrq and the filename exists.
     * If so, server opens a new port to process client request in sendFile method
     * If file does not exist, server sends back an error packet to client
     */
    @Override
    public void run() {
        /*
         * parse the request packet, ensuring that it is a RRQ
         * and then call sendfile
         */
        String fileName = "";
        InetAddress clientIP = req.getAddress();
        int clientPort = req.getPort();

        try {
            // parse the request packet
            ByteArrayInputStream byteStream = new ByteArrayInputStream(req.getData());
            DataInputStream inputStream = new DataInputStream(byteStream);

            // read type
            byte type = inputStream.readByte();
            // debugging
            System.out.println("req Type: " + type);

            // check if read request, if so call readfile method and get filename
            if (type == RRQ) {
                fileName = readFile(inputStream);
                // debugging
                System.out.println("File: " + fileName);
            }

            File file = new File(fileName.trim());
            // if file does not exist
            if (!file.exists()) {
                // print file not found
                System.out.println("File not found: " + fileName);
                // send error packet
                sendErrorPacket(clientIP, clientPort, "File not found");
            } else {
                // send file
                sendfile(file, clientIP, clientPort);

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * his method creates new socket with a random port > 50000 and sends file in chunks of 512 bytes
     * Calls WaitForAck method before sending another packet
     * @param file file to be sent
     * @param clientIP client's IP address
     * @param clientPort client's port number
     */
    private void sendfile(File file, InetAddress clientIP, int clientPort) {
        int blockNum = 1;
        byte[] buf = new byte[512];
        int readBytes;
        int currentResendAttempts = 0;

        try {
            // create new socket with random port above 50000 
            DatagramSocket socket = new DatagramSocket(50000 + (int) (Math.random() * 10000));
            FileInputStream fileInputStream = new FileInputStream(file);

            while (true) {
                //ensure packet does not cap at 255
                if(blockNum >=255){
                    blockNum = 1;
                }

                //reads file to be sent
                readBytes = fileInputStream.read(buf);

                //byte Array to be placed in datagramPacket
                byte[] byteArray;
                //if packet has no bytes(is a multiple of 512), send Datagram packet with empty byte array 
                if(readBytes <= -1){
                    byteArray = new byte[]{DATA, (byte)blockNum};
                }else{
                    byteArray = getDataPacket(buf, blockNum, readBytes);
                }
                DatagramPacket datagramPacket = new DatagramPacket(byteArray, byteArray.length, clientIP,
                            clientPort);
                // send packet
                socket.send(datagramPacket);

                //wait for ACK packet 
                currentResendAttempts = waitForAck(socket, blockNum, datagramPacket);

                //increment blockNum
                blockNum++;

                 //check if end of file
                if(readBytes < 512){
                    System.out.println("file sent");
                    fileInputStream.close();
                    break; //exit loop
                }

                //if packet was resent more than 5 times, close connection with client
                if(currentResendAttempts >= 5){
                    System.out.println("Connection closed at max resend attempts reached");
                    sendErrorPacket(clientIP, clientPort, "Connection closed as max resend attemps reached");
                    break;
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * this method waits for ack packet from client with a timeout of 1 sec
     * if timeout occurs, restransmits packet, returns number of times restransmit happens
     * @param socket
     * @param blockNum
     * @param packet
     * @return
     */
    private int waitForAck(DatagramSocket socket, int blockNum, DatagramPacket packet) {
        int resendCounter = 1;

        // create ackPacket to wait for ACK from client
        byte[] ackByteArray = new byte[2];
        DatagramPacket ackPacket = new DatagramPacket(ackByteArray, ackByteArray.length);

        while (true) {

            try {
                // set timeout to receive ACK
                socket.setSoTimeout(1000);
                //wait to receive ack from client
                socket.receive(ackPacket);

                //get block num from second byte received, ensure it is unsigned so blockNum ranges from(0 -255)
                int ackBlockNum = ackPacket.getData()[1] & 0xFF;
                System.out.println("Ack with BlockNum received: " + ackBlockNum);
                // if ackBlockNum is the same as blockNum, packet was received by client so
                // break out of loop
                if (ackBlockNum == blockNum) {
                    break;
          }

            } catch (SocketTimeoutException e) {
                // handle timeout Exception
                System.out.println("Timeout, resending packet");
                try {
                    // restransmit packet if less than 5 retransmits
                    if (resendCounter >= 5) {
                        return resendCounter;
                    } else {
                        socket.send(packet);
                        resendCounter++;
                    }
                } catch (IOException d) {
                    d.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return resendCounter;

    }

    /**
     * getDataPacket adds contents of data packet to be sent, to a dataOutputStream
     * and returns a byteArray 
     * @param data     byte array
     * @param blockNum blockNum identifier for packet
     * @return packet to be sent
     */
    private byte[] getDataPacket(byte[] data, int blockNum, int length) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(byteStream);
        try {
            //write the type and blockNum
            outputStream.writeByte(DATA);
            outputStream.writeByte(blockNum);
            //write data
            outputStream.write(data, 0, length);
        } catch (IOException e) {
            System.out.println(e);
        }

        return byteStream.toByteArray();
    }

    /**
     * this method takes in DatainputStream and reads the data, adding the data to
     * bytesRead and returns the Filename as a string
     * @param inputStream data to read
     * @return String variable of filename
     */
    private String readFile(DataInputStream inputStream) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        String fileName = "";
        try {
            int bytesRead;
            
            while ((bytesRead = inputStream.read()) != -1) {
                byteStream.write(bytesRead);
            }
            fileName = byteStream.toString("UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return fileName;
    }

    /**
     * method opens an output stream and adds content of error packet to it, sending
     * it to the client
     */
    private void sendErrorPacket(InetAddress clientIP, int clientPort, String msg) {
        // constructs binary representation of the error packet
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        // write data into bytestream
        DataOutputStream outputStream = new DataOutputStream(byteStream);

        // error message
        String errorMessage = msg;

        try {
            // type
            outputStream.writeByte(ERROR);
            // message
            outputStream.write(errorMessage.getBytes("UTF-8"));
            // null byte to terminate error message
            outputStream.write(0);

            // converts data in bytestream to byte array
            byte[] errorPacket = byteStream.toByteArray();
            DatagramPacket errorDatagramPacket = new DatagramPacket(errorPacket, errorPacket.length, clientIP,
                    clientPort);

            // send error packet to client
            DatagramSocket errorDatagramSocket = new DatagramSocket();
            errorDatagramSocket.send(errorDatagramPacket);

            errorDatagramSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}