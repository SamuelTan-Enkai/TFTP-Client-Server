import java.io.*;
import java.net.*;
import java.util.Arrays;

public class TftpClient {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: TftpClient <servername> <filename>");
            return;
        }

        String serverName = args[0];
        String fileName = args[1];
        int serverPort = 50000;
        //instantiate client
        TftpClient client = new TftpClient();

        try {
            DatagramSocket datagramSocket = new DatagramSocket();
            InetAddress serverIP = InetAddress.getByName(serverName);

            // create and send RRQ packet
            byte[] rrqPacket = client.createRRQ(fileName);
            DatagramPacket rrqDatagramPacket = new DatagramPacket(rrqPacket, rrqPacket.length, serverIP, serverPort);
            datagramSocket.send(rrqDatagramPacket);

            int blockNum = 1;

            // accumulate received file content
            FileOutputStream fileOutputStream = new FileOutputStream(fileName);

            while (true) {
                // receive packets from server of length 514
                byte[] receivePacket = new byte[514];
                DatagramPacket receivedDatagramPacket = new DatagramPacket(receivePacket, receivePacket.length);
                datagramSocket.receive(receivedDatagramPacket);

                // check if received packet is an error packet
                if (receivedDatagramPacket.getData()[0] == 4) {
                    handleErrorPacket(receivedDatagramPacket);
                    datagramSocket.close();
                    fileOutputStream.close();
                    return;
                }

                // get received blockNum stored in 2nd byte and ensure byte is unsigned
                int receivedBlockNum = receivedDatagramPacket.getData()[1] & 0xff;
                System.out.println("blockNum of packet received: " + receivedBlockNum);

                // ensure packet does not cap at 255
                if (blockNum >= 255) {
                    blockNum = 1;
                }

                // check if packet received is the expected packet
                if (receivedBlockNum == blockNum) {
                    // add received data to fileContent
                    byte[] data = Arrays.copyOfRange(receivedDatagramPacket.getData(), 2,
                            receivedDatagramPacket.getLength());
                    fileOutputStream.write(data);

                    // send ACK
                    byte[] ackArray = client.createAck(blockNum);
                    DatagramPacket ackDatagramPacket = new DatagramPacket(ackArray, ackArray.length, serverIP,
                            receivedDatagramPacket.getPort());
                    datagramSocket.send(ackDatagramPacket);
                    System.out.println("ACK with blockNum: " + blockNum + ", sent");

                    // check if last packet(length would be less than 512 regardless)
                    if (receivedDatagramPacket.getLength() < 512) {
                        System.out.println("transfer done");
                        datagramSocket.close();
                        fileOutputStream.close();
                        break;
                    }
                    blockNum++;

                } else {
                    // Previous ACK was lost, so resend ACK
                    byte[] ackArray = client.createAck(blockNum - 1);
                    DatagramPacket ackDatagramPacket = new DatagramPacket(ackArray, ackArray.length, serverIP,
                            receivedDatagramPacket.getPort());
                    datagramSocket.send(ackDatagramPacket);
                    System.out.println("resent ACK with blockNum: " + blockNum + ", sent");

                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * this method extracts error message from packet and prints to server
     * @param errorPacket packet containing message to be extracted
     */
    private static void handleErrorPacket(DatagramPacket errorPacket) {
        String errorMsg = new String(errorPacket.getData(), 1, errorPacket.getLength());
        System.out.println("Error Packet received: " + errorMsg);

    }

    /**
     * createAck method creates the byteArray of the ACK packet
     * @param blockNum blockNum to be inserted into the 2nd byte of packet
     * @return byteArray of ack packet
     */
    private byte[] createAck(int blockNum) {
        byte[] ackPacket = new byte[2];
        // type is set to ack(3)
        ackPacket[0] = 3;
        // blockNum
        ackPacket[1] = (byte) blockNum;

        return ackPacket;
    }

    /**
     * this method creates the byteArray of the RRQ packet
     * @param fileName name of file to be added to RRQ
     * @return byteArray of rrq packet
     */
    private byte[] createRRQ(String fileName) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // rrq type
            outputStream.write(1);
            //ensure filename is UTF-8
            outputStream.write(fileName.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputStream.toByteArray();
    }
}
