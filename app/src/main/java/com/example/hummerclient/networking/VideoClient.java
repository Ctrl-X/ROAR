package com.example.hummerclient.networking;/* ------------------
   Client
   usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
   ---------------------- */

import android.media.Image;

import com.example.hummerclient.video.VideoViewModel;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

public class VideoClient extends Thread {

    private final VideoViewModel videoViewModel;
    private final String ServerHost;
    private final int RTSP_server_port;
    //RTP variables:
    //----------------
    DatagramPacket rcvdp;            //UDP packet received from the server
    DatagramSocket RTPsocket;        //socket to be used to send and receive UDP packets
    static int RTP_RCV_PORT = UDP_PORT.CLIENT_RTP_RCV.getValue(); //port where the client will receive the RTP packets

    Timer timer; //timer used to receive data from the UDP socket
    byte[] buf;  //buffer used to store data received from the server

    //RTSP variables
    //----------------
    //rtsp states
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    static int serverState = INIT;
    Socket RTSPsocket;           //socket used to send/receive RTSP messages
    InetAddress ServerIPAddr;

    //input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName = "DUMMY"; //video file to request to the server
    int RTSPSeqNb = 0;           //Sequence number of RTSP messages within the session
    String RTSPid;              // ID of the RTSP session (given by the RTSP Server)

    final static String CRLF = "\r\n";
    final static String DES_FNAME = "session_info.txt";

    //RTCP variables
    //----------------
    DatagramSocket RTCPsocket;          //UDP socket for sending RTCP packets
    static int RTCP_RCV_PORT = UDP_PORT.CLIENT_RTCP_RCV_PORT.getValue();   //port where the client will receive the RTP packets
    static int RTCP_PERIOD = 400;       //How often to send RTCP packets
    RtcpSender rtcpSender;

    //Video constants:
    //------------------
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video

    //Statistics variables:
    //------------------
    double statDataRate;        //Rate of video data received in bytes/s
    int statTotalBytes;         //Total number of bytes received in a session
    double statStartTime;       //Time in milliseconds when start is pressed
    double statTotalPlayTime;   //Time in milliseconds of video playing since beginning
    float statFractionLost;     //Fraction of RTP data packets from sender lost since the prev packet was sent
    int statCumLost;            //Number of packets lost
    int statExpRtpNb;           //Expected Sequence number of RTP messages within the session
    int statHighSeqNb;          //Highest sequence number received in session

    FrameSynchronizer fsynch;

    //--------------------------
    //Constructor
    //--------------------------
    public VideoClient(VideoViewModel videoModel, String serverIp, UDP_PORT RTSPport) {


        //init RTCP packet sender
        rtcpSender = new RtcpSender(400);

        //allocate enough memory for the buffer used to receive data from the server
        buf = new byte[15000];

        //create the frame synchronizer
        fsynch = new FrameSynchronizer(100);

        videoViewModel = videoModel;

        RTSP_server_port = RTSPport.getValue();
        ServerHost = serverIp;

    }

    //------------------------------------
    //main
    //------------------------------------
    public void run() {
        while (!interrupted()) {
            // Try to make the setup every 5 seconde (in case of loosing connection)
            try {
                if (serverState == INIT) {
                    this.setup();
                }else{
                    this.ping();
                }
            } catch (SocketException se) {
                System.out.println("Socket exception: " + se);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void kill() {
        stopTimer();
        if (this.isAlive()) {
            this.interrupt();
        }
    }

    private void stopTimer() {
        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
    }

    //------------------------------------
    //Handler for buttons
    //------------------------------------

    //Handler for Setup button
    //-----------------------
    public void setup() throws IOException {


        //Create a Client object

        //get server RTSP port and IP address from the command line
        //------------------
        this.ServerIPAddr = InetAddress.getByName(ServerHost);


        //Establish a TCP connection with the server to exchange RTSP messages
        //------------------
        this.RTSPsocket = new Socket(this.ServerIPAddr, RTSP_server_port);

        //Establish a UDP connection with the server to exchange RTCP control packets
        //------------------

        //Set input and output stream filters:
        RTSPBufferedReader = new BufferedReader(new InputStreamReader(this.RTSPsocket.getInputStream()));
        RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(this.RTSPsocket.getOutputStream()));


        //Init non-blocking RTPsocket that will be used to receive data
        //construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
        RTPsocket = new DatagramSocket(RTP_RCV_PORT);
        //UDP socket for sending QoS RTCP packets
        RTCPsocket = new DatagramSocket();
        //set TimeOut value of the socket to 5msec.
        RTPsocket.setSoTimeout(5);


        //init RTSP sequence number
        RTSPSeqNb = 1;

        //Send SETUP message to the server
        sendRequest("SETUP");

        //Wait for the response
        if (parseServerResponse() != 200)
            System.out.println("Invalid Server Response");
        else {
            if (serverState == READY) {
                //change RTSP state and print new state
                this.play();
                System.out.println("New RTSP state: READY");
            }
        }

    }

    //Handler for Play button
    //-----------------------
    public void play() {


        if (serverState == READY) {
            //Start to save the time in stats
            statStartTime = System.currentTimeMillis();
            //increase RTSP sequence number
            RTSPSeqNb++;

            //Send PLAY message to the server
            sendRequest("PLAY");

            //Wait for the response
            if (parseServerResponse() != 200) {
                System.out.println("Invalid Server Response");
            } else {
                //change RTSP state and print out new state
                System.out.println("New RTSP state: PLAYING");

                //start the timer
                timer = new Timer();
                timer.schedule(receiveImageTask, 0, 20);
                rtcpSender.startSend();
            }
        }
        //else if state != READY then do nothing

    }

    //Handler for Pause button
    //-----------------------
    public void pause() {

        System.out.println("Pause Button pressed!");

        if (serverState == PLAYING) {
            //increase RTSP sequence number
            RTSPSeqNb++;

            //Send PAUSE message to the server
            sendRequest("PAUSE");

            //Wait for the response
            if (parseServerResponse() != 200)
                System.out.println("Invalid Server Response");
            else {
                //change RTSP state and print out new state
                System.out.println("New RTSP state: READY");

                //stop the timer
                stopTimer();
                rtcpSender.stopSend();
            }
        }
        //else if state != PLAYING then do nothing

    }

    //Handler for Teardown button
    //-----------------------
    public void tear() {
        System.out.println("Teardown Button pressed !");

        //increase RTSP sequence number
        RTSPSeqNb++;

        //Send TEARDOWN message to the server
        sendRequest("TEARDOWN");

        //Wait for the response
        if (parseServerResponse() != 200)
            System.out.println("Invalid Server Response");
        else {
            //change RTSP state and print out new state
            System.out.println("New RTSP state: INIT");

            //stop the timer
            stopTimer();
            rtcpSender.stopSend();

            //exit
            System.exit(0);
        }

    }

    // Get information about the data stream
    public void describe() {
        System.out.println("Sending DESCRIBE request");

        //increase RTSP sequence number
        RTSPSeqNb++;

        //Send DESCRIBE message to the server
        sendRequest("DESCRIBE");

        //Wait for the response
        if (parseServerResponse() != 200) {
            System.out.println("Invalid Server Response");
        } else {
            System.out.println("Received response for DESCRIBE");
        }

    }

    // Get information about the data stream
    public void ping() {
        System.out.println("Sending PING request");

        //increase RTSP sequence number
        RTSPSeqNb++;

        //Send DESCRIBE message to the server
        sendRequest("PING");

        //Wait for the response
        if (parseServerResponse() != 200) {
            System.out.println("Invalid Server Response");
        } else {
            System.out.println("Received response for PING");
        }

    }

    //------------------------------------
    //Handler for timer
    //------------------------------------
    TimerTask receiveImageTask = new TimerTask() {
        public void run() {

            //Construct a DatagramPacket to receive data from the UDP socket
            rcvdp = new DatagramPacket(buf, buf.length);

            try {
                //receive the DP from the socket, save time for stats
                RTPsocket.receive(rcvdp);

                double curTime = System.currentTimeMillis();
                statTotalPlayTime += curTime - statStartTime;
                statStartTime = curTime;

                //create an RTPpacket object from the DP
                RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
                int seqNb = rtp_packet.getsequencenumber();

                //this is the highest seq num received

                //print important header fields of the RTP packet received:
                System.out.println("Got RTP packet with SeqNum # " + seqNb
                        + " TimeStamp " + rtp_packet.gettimestamp() + " ms, of type "
                        + rtp_packet.getpayloadtype());

                //print header bitstream:
                rtp_packet.printheader();

                //get the payload bitstream from the RTPpacket object
                int payload_length = rtp_packet.getpayload_length();
                byte[] payload = new byte[payload_length];
                rtp_packet.getpayload(payload);
                videoViewModel.setImageBuffer(payload);

                //compute stats and update the label in GUI
                statExpRtpNb++;
                if (seqNb > statHighSeqNb) {
                    statHighSeqNb = seqNb;
                }
                if (statExpRtpNb != seqNb) {
                    statCumLost++;
                }
                statDataRate = statTotalPlayTime == 0 ? 0 : (statTotalBytes / (statTotalPlayTime / 1000.0));
                statFractionLost = (float) statCumLost / statHighSeqNb;
                statTotalBytes += payload_length;
                updateStatsLabel();

                //get an Image object from the payload bitstream
//                Toolkit toolkit = Toolkit.getDefaultToolkit();
//                fsynch.addFrame(toolkit.createImage(payload, 0, payload_length), seqNb);

                //display the image as an ImageIcon object
//                icon = new ImageIcon(fsynch.nextFrame());
//                iconLabel.setIcon(icon);
            } catch (InterruptedIOException iioe) {
                System.out.println("Nothing to read");
            } catch (IOException ioe) {
                System.out.println("Exception caught: " + ioe);
            }
        }
    };

    //------------------------------------
    // Send RTCP control packets for QoS feedback
    //------------------------------------
    class RtcpSender {

        private Timer rtcpTimer;
        int interval;

        // Stats variables
        private int numPktsExpected;    // Number of RTP packets expected since the last RTCP packet
        private int numPktsLost;        // Number of RTP packets lost since the last RTCP packet
        private int lastHighSeqNb;      // The last highest Seq number received
        private int lastCumLost;        // The last cumulative packets lost
        private float lastFractionLost; // The last fraction lost

        Random randomGenerator;         // For testing only

        public RtcpSender(int interval) {
            this.interval = interval;
            randomGenerator = new Random();
        }

        public void run() {
            System.out.println("RtcpSender Thread Running");
        }

        TimerTask rtcpSendControlTask = new TimerTask() {
            public void run() {
                // Calculate the stats for this period
                numPktsExpected = statHighSeqNb - lastHighSeqNb;
                numPktsLost = statCumLost - lastCumLost;
                lastFractionLost = numPktsExpected == 0 ? 0f : (float) numPktsLost / numPktsExpected;
                lastHighSeqNb = statHighSeqNb;
                lastCumLost = statCumLost;

                //To test lost feedback on lost packets
                // lastFractionLost = randomGenerator.nextInt(10)/10.0f;
                try {

                    RTCPpacket rtcp_packet = new RTCPpacket(lastFractionLost, statCumLost, statHighSeqNb);
                    int packet_length = rtcp_packet.getlength();
                    byte[] packet_bits = new byte[packet_length];
                    rtcp_packet.getpacket(packet_bits);

                    DatagramPacket dp = new DatagramPacket(packet_bits, packet_length, ServerIPAddr, RTCP_RCV_PORT);
                    RTCPsocket.send(dp);
                } catch (InterruptedIOException iioe) {
                    System.out.println("RTCP Control Nothing to read");
                } catch (IOException ioe) {
                    System.out.println("Exception caught: " + ioe);
                }
            }
        };

        // Start sending RTCP packets
        public void startSend() {
            this.rtcpTimer = new Timer();
            this.rtcpTimer.schedule(rtcpSendControlTask, 0, this.interval);
        }

        // Stop sending RTCP packets
        public void stopSend() {
            if (this.rtcpTimer != null) {
                this.rtcpTimer.cancel();
                this.rtcpTimer = null;
            }
        }
    }

    //------------------------------------
    //Synchronize frames
    //------------------------------------
    class FrameSynchronizer {

        private ArrayDeque<Image> queue;
        private int bufSize;
        private int curSeqNb;
        private Image lastImage;

        public FrameSynchronizer(int bsize) {
            curSeqNb = 1;
            bufSize = bsize;
            queue = new ArrayDeque<Image>(bufSize);
        }

        //synchronize frames based on their sequence number
        public void addFrame(Image image, int seqNum) {
            if (seqNum < curSeqNb) {
                queue.add(lastImage);
            } else if (seqNum > curSeqNb) {
                for (int i = curSeqNb; i < seqNum; i++) {
                    queue.add(lastImage);
                }
                queue.add(image);
            } else {
                queue.add(image);
            }
        }

        //get the next synchronized frame
        public Image nextFrame() {
            curSeqNb++;
            lastImage = queue.peekLast();
            return queue.remove();
        }
    }

    //------------------------------------
    //Parse Server Response
    //------------------------------------
    private int parseServerResponse() {
        int reply_code = 0;

        try {
            //parse status line and extract the reply_code:
            String StatusLine = RTSPBufferedReader.readLine();
            if(StatusLine == null){
                // No response from the server : The socket has been closed
                serverState = INIT;
            }else {
                System.out.println("RTSP Client - Received from Server:");
                System.out.println(StatusLine);

                StringTokenizer tokens = new StringTokenizer(StatusLine);
                tokens.nextToken(); //skip over the RTSP version
                reply_code = Integer.parseInt(tokens.nextToken());
                serverState = Integer.parseInt(tokens.nextToken());

                //if reply code is OK get and print the 2 other lines
                if (reply_code == 200) {
                    String SeqNumLine = RTSPBufferedReader.readLine();
                    System.out.println(SeqNumLine);

                    String SessionLine = RTSPBufferedReader.readLine();
                    System.out.println(SessionLine);

                    tokens = new StringTokenizer(SessionLine);
                    String temp = tokens.nextToken();
                    //if state == INIT gets the Session Id from the SessionLine
//                if (state == INIT && temp.compareTo("Session:") == 0) {
                    if (serverState == INIT && temp.compareTo("Session:") == 0) {
                        RTSPid = tokens.nextToken();
                    } else if (temp.compareTo("Content-Base:") == 0) {
                        // Get the DESCRIBE lines
                        String newLine;
                        for (int i = 0; i < 6; i++) {
                            newLine = RTSPBufferedReader.readLine();
                            System.out.println(newLine);
                        }
                    }
                } else if (reply_code == 100) {
                    // Server still alive
                }
            }
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
        }

        return (reply_code);
    }

    private void updateStatsLabel() {
        DecimalFormat formatter = new DecimalFormat("###,###.##");
//        statLabel1.setText("Total Bytes Received: " + statTotalBytes);
//        statLabel2.setText("Packet Lost Rate: " + formatter.format(statFractionLost));
//        statLabel3.setText("Data Rate: " + formatter.format(statDataRate) + " bytes/s");
    }

    //------------------------------------
    //Send RTSP Request
    //------------------------------------

    private void sendRequest(String request_type) {
        try {
            //Use the RTSPBufferedWriter to write to the RTSP socket

            //write the request line:
            RTSPBufferedWriter.write(request_type + " " + VideoFileName + " RTSP/1.0" + CRLF);

            //write the CSeq line: 
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);

            //check if request_type is equal to "SETUP" and in this case write the 
            //Transport: line advertising to the server the port used to receive 
            //the RTP packets RTP_RCV_PORT
            if (request_type == "SETUP") {
                RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
            } else if (request_type == "DESCRIBE") {
                RTSPBufferedWriter.write("Accept: application/sdp" + CRLF);
            } else {
                //otherwise, write the Session line from the RTSPid field
                RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
            }

            RTSPBufferedWriter.flush();
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }
}
