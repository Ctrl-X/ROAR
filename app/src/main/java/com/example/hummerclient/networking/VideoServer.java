package com.example.hummerclient.networking;/* ------------------
   Server
   usage: java Server [RTSP listening port]
   ---------------------- */


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.example.hummerclient.video.VideoViewModel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class VideoServer {

    //RTP variables:
    //----------------
    DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
    DatagramPacket senddp; //UDP packet containing the video frames

    InetAddress ClientIPAddr;   //Client IP address
    int RTP_dest_port = 0;      //destination port for RTP packets  (given by the RTSP Client)
    int RTSP_dest_port = 0;


    //Video variables:
    //----------------
    int imagenb = 0; //image nb of the image currently transmitted
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
    static int FRAME_PERIOD = 100; //Frame period of the video to stream, in ms
    static int VIDEO_LENGTH = 500; //length of the video in frames

    Timer timer;    //timer used to send the images at the video frame rate
    int sendDelay;  //the delay to send images over the wire. Ideally should be
    //equal to the frame rate of the video file, but may be
    //adjusted when congestion is detected.

    //RTSP variables
    //----------------
    //rtsp states
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    //rtsp message types
    final static int SETUP = 3;
    final static int PLAY = 4;
    final static int PAUSE = 5;
    final static int TEARDOWN = 6;
    final static int DESCRIBE = 7;

    static int state; //RTSP Server state == INIT or READY or PLAY
    Socket RTSPsocket; //socket used to send/receive RTSP messages
    //input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; //video file requested from the client
    static String RTSPid = UUID.randomUUID().toString(); //ID of the RTSP session
    int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session


    //RTCP variables
    //----------------
    static int RTCP_RCV_PORT = 19001; //port where the client will receive the RTP packets
    static int RTCP_PERIOD = 400;     //How often to check for control events
    DatagramSocket RTCPsocket;
    RtcpReceiver rtcpReceiver;
    int congestionLevel;

    //Performance optimization and Congestion control
    ImageTranslator imgTranslator;
    CongestionController cc;

    final static String CRLF = "\r\n";

    Context context;
    VideoViewModel videoViewModel;

    //--------------------------------
    //Constructor
    //--------------------------------
    public VideoServer(Context ctx, VideoViewModel viewModel) {

        //init RTP sending Timer
        sendDelay = FRAME_PERIOD;
        timer = null;
        context = ctx;
        videoViewModel = viewModel;


        //init congestion controller
        cc = new CongestionController(600);

        //init the RTCP packet receiver
        rtcpReceiver = new RtcpReceiver(RTCP_PERIOD);

        //Video encoding and quality
        imgTranslator = new ImageTranslator(.8f);
    }

    //------------------------------------
    //main
    //------------------------------------
    public void start(int RTSPport) throws Exception {

        //get RTSP socket port from the command line
        this.RTSP_dest_port = RTSPport;

        //Initiate TCP connection with the client for the RTSP session
        ServerSocket listenSocket = new ServerSocket(RTSPport);
        this.RTSPsocket = listenSocket.accept();
        listenSocket.close();

        //Get Client IP address
        this.ClientIPAddr = this.RTSPsocket.getInetAddress();

        //Initiate RTSPstate
        state = INIT;

        //Set input and output stream filters:
        RTSPBufferedReader = new BufferedReader(new InputStreamReader(this.RTSPsocket.getInputStream()));
        RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(this.RTSPsocket.getOutputStream()));

        //Wait for the SETUP message from the client
        int request_type;
        boolean done = false;
        while (!done) {
            request_type = this.parseRequest(); //blocking

            if (request_type == SETUP) {
                done = true;

                //update RTSP state
                state = READY;
                System.out.println("New RTSP state: READY");

                //Send response
                this.sendResponse();

                //init the VideoStream object:

                //init RTP and RTCP sockets
                this.RTPsocket = new DatagramSocket();
                this.RTCPsocket = new DatagramSocket(RTCP_RCV_PORT);
            }
        }

        //loop to handle RTSP requests
        while (true) {
            //parse the request
            request_type = this.parseRequest(); //blocking

            if ((request_type == PLAY) && (state == READY)) {
                //send back response
                this.sendResponse();
                //start timer
                this.timer = new Timer();
                this.timer.schedule(sendImageTask, 0, sendDelay);
                this.rtcpReceiver.startRcv();
                //update state
                state = PLAYING;
                System.out.println("New RTSP state: PLAYING");
            } else if ((request_type == PAUSE) && (state == PLAYING)) {
                //send back response
                this.sendResponse();
                this.stopTimer();
                this.rtcpReceiver.stopRcv();
                //update state
                state = READY;
                System.out.println("New RTSP state: READY");
            } else if (request_type == TEARDOWN) {
                //send back response
                this.sendResponse();
                this.stopTimer();
                this.rtcpReceiver.stopRcv();
                //close sockets
                this.RTSPsocket.close();
                this.RTPsocket.close();

                System.exit(0);
            } else if (request_type == DESCRIBE) {
                System.out.println("Received DESCRIBE request");
                this.sendDescribe();
            }
        }
    }

    private void stopTimer() {
        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
    }


    public void stop() {
        this.stopTimer();
        if (cc != null) {
            cc.stop();
        }
        rtcpReceiver.stopRcv();
    }

    TimerTask sendImageTask = new TimerTask() {
        public void run() {
            byte[] frame;

            //if the current image nb is less than the length of the video
            if (imagenb < VIDEO_LENGTH) {
                //update current imagenb
                imagenb++;

                try {
                    //get next frame to send from the video, as well as its size
                    byte[] imgBuffer = videoViewModel.getImageBuffer().array();

                    int image_length = imgBuffer.length;

                    //adjust quality of the image if there is congestion detected
                    if (congestionLevel > 0) {
                        imgTranslator.setCompressionQuality(1.0f - congestionLevel * 0.2f);
                        frame = imgTranslator.compress(Arrays.copyOfRange(imgBuffer, 0, image_length));
                        image_length = frame.length;
                        System.arraycopy(frame, 0, imgBuffer, 0, image_length);
                    }

                    //Builds an RTPpacket object containing the frame
                    RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb * FRAME_PERIOD, imgBuffer, image_length);

                    //get to total length of the full rtp packet to send
                    int packet_length = rtp_packet.getlength();

                    //retrieve the packet bitstream and store it in an array of bytes
                    byte[] packet_bits = new byte[packet_length];
                    rtp_packet.getpacket(packet_bits);

                    //send the packet as a DatagramPacket over the UDP socket
                    senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
                    RTPsocket.send(senddp);

                    System.out.println("Send frame #" + imagenb + ", Frame size: " + image_length + " (" + imgBuffer.length + ")");
                    //print the header bitstream
                    rtp_packet.printheader();

                } catch (Exception ex) {
                    System.out.println("Exception caught: " + ex);
                    System.exit(0);
                }
            } else {
                //if we have reached the end of the video file, stop the timer
                stopTimer();
                rtcpReceiver.stopRcv();
            }
        }
    };


    //------------------------
    //Controls RTP sending rate based on traffic
    //------------------------
    class CongestionController {
        private Timer ccTimer;
        int interval;   //interval to check traffic stats
        int prevLevel;  //previously sampled congestion level

        public CongestionController(int interval) {
            this.interval = interval;
            this.ccTimer = new Timer();
            this.ccTimer.schedule(ccSendImageTask, 0, interval);

        }

        TimerTask ccSendImageTask = new TimerTask() {
            public void run() {
                //adjust the send rate
                if (prevLevel != congestionLevel) {
                    sendDelay = FRAME_PERIOD + congestionLevel * (int) (FRAME_PERIOD * 0.1);
                    if (timer != null) {
                        timer.cancel();
                        timer = new Timer();
                        timer.schedule(sendImageTask, sendDelay, sendDelay);
                    }
                    prevLevel = congestionLevel;
                    System.out.println("Send delay changed to: " + sendDelay);
                }
            }
        };

        private void stop() {
            if (this.ccTimer != null) {
                this.ccTimer.cancel();
                this.ccTimer = null;
            }
        }
    }

    //------------------------
    //Listener for RTCP packets sent from client
    //------------------------
    class RtcpReceiver {
        private Timer rtcpTimer;
        private byte[] rtcpBuf;
        int interval;

        public RtcpReceiver(int interval) {
            //set timer with interval for receiving packets
            this.interval = interval;

            //allocate buffer for receiving RTCP packets
            rtcpBuf = new byte[512];
        }

        TimerTask rtcpSendImageTask = new TimerTask() {
            public void run() {
                //Construct a DatagramPacket to receive data from the UDP socket
                DatagramPacket dp = new DatagramPacket(rtcpBuf, rtcpBuf.length);
                float fractionLost;

                try {
                    RTCPsocket.receive(dp);   // Blocking
                    RTCPpacket rtcpPkt = new RTCPpacket(dp.getData(), dp.getLength());
                    System.out.println("[RTCP] " + rtcpPkt);

                    //set congestion level between 0 to 4
                    fractionLost = rtcpPkt.fractionLost;
                    if (fractionLost >= 0 && fractionLost <= 0.01) {
                        congestionLevel = 0;    //less than 0.01 assume negligible
                    } else if (fractionLost > 0.01 && fractionLost <= 0.25) {
                        congestionLevel = 1;
                    } else if (fractionLost > 0.25 && fractionLost <= 0.5) {
                        congestionLevel = 2;
                    } else if (fractionLost > 0.5 && fractionLost <= 0.75) {
                        congestionLevel = 3;
                    } else {
                        congestionLevel = 4;
                    }
                } catch (InterruptedIOException iioe) {
                    System.out.println("Nothing to read");
                } catch (IOException ioe) {
                    System.out.println("Exception caught: " + ioe);
                }
            }
        };

        public void startRcv() {
            this.rtcpTimer = new Timer();
            this.rtcpTimer.schedule(rtcpSendImageTask, 0, interval);
        }

        public void stopRcv() {
            if (this.rtcpTimer != null) {
                this.rtcpTimer.cancel();
                this.rtcpTimer = null;
            }
        }
    }

    //------------------------------------
    //Translate an image to different encoding or quality
    //------------------------------------
    class ImageTranslator {

        private int compressionQuality;

        public ImageTranslator(float cq) {
            setCompressionQuality(cq);

        }

        public byte[] compress(byte[] imageBytes) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                bmp.compress(Bitmap.CompressFormat.JPEG, compressionQuality, bos); //100-best quality
            } catch (Exception ex) {
                System.out.println("Exception caught: " + ex);
                System.exit(0);
            }
            return bos.toByteArray();
        }

        public void setCompressionQuality(float cq) {
            compressionQuality = (int) (cq * 100.0f);
        }
    }

    //------------------------------------
    //Parse RTSP Request
    //------------------------------------
    private int parseRequest() {
        int request_type = -1;
        try {
            //parse request line and extract the request_type:
            String RequestLine = RTSPBufferedReader.readLine();
            System.out.println("RTSP Server - Received from Client:");
            System.out.println(RequestLine);

            StringTokenizer tokens = new StringTokenizer(RequestLine);
            String request_type_string = tokens.nextToken();

            //convert to request_type structure:
            if ((new String(request_type_string)).compareTo("SETUP") == 0)
                request_type = SETUP;
            else if ((new String(request_type_string)).compareTo("PLAY") == 0)
                request_type = PLAY;
            else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
                request_type = PAUSE;
            else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
                request_type = TEARDOWN;
            else if ((new String(request_type_string)).compareTo("DESCRIBE") == 0)
                request_type = DESCRIBE;

            if (request_type == SETUP) {
                //extract VideoFileName from RequestLine
                VideoFileName = tokens.nextToken();
            }

            //parse the SeqNumLine and extract CSeq field
            String SeqNumLine = RTSPBufferedReader.readLine();
            System.out.println(SeqNumLine);
            tokens = new StringTokenizer(SeqNumLine);
            tokens.nextToken();
            RTSPSeqNb = Integer.parseInt(tokens.nextToken());

            //get LastLine
            String LastLine = RTSPBufferedReader.readLine();
            System.out.println(LastLine);

            tokens = new StringTokenizer(LastLine);
            if (request_type == SETUP) {
                //extract RTP_dest_port from LastLine
                for (int i = 0; i < 3; i++)
                    tokens.nextToken(); //skip unused stuff
                RTP_dest_port = Integer.parseInt(tokens.nextToken());
            } else if (request_type == DESCRIBE) {
                tokens.nextToken();
                String describeDataType = tokens.nextToken();
            } else {
                //otherwise LastLine will be the SessionId line
                tokens.nextToken(); //skip Session:
                RTSPid = tokens.nextToken();
            }
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }

        return (request_type);
    }

    // Creates a DESCRIBE response string in SDP format for current media
    private String describe() {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();

        // Write the body first so we can get the size later
        writer2.write("v=0" + CRLF);
        writer2.write("m=video " + RTSP_dest_port + " RTP/AVP " + MJPEG_TYPE + CRLF);
        writer2.write("a=control:streamid=" + RTSPid + CRLF);
        writer2.write("a=mimetype:string;\"video/MJPEG\"" + CRLF);
        String body = writer2.toString();

        writer1.write("Content-Base: " + VideoFileName + CRLF);
        writer1.write("Content-Type: " + "application/sdp" + CRLF);
        writer1.write("Content-Length: " + body.length() + CRLF);
        writer1.write(body);

        return writer1.toString();
    }

    //------------------------------------
    //Send RTSP Response
    //------------------------------------
    private void sendResponse() {
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
            RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
            RTSPBufferedWriter.flush();
            System.out.println("RTSP Server - Sent response to Client.");
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }

    private void sendDescribe() {
        String des = describe();
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
            RTSPBufferedWriter.write(des);
            RTSPBufferedWriter.flush();
            System.out.println("RTSP Server - Sent response to Client.");
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }
}
