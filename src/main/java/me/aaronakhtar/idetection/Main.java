package me.aaronakhtar.idetection;

import me.aaronakhtar.idetection.threads.MbpsThread;
import me.aaronakhtar.idetection.threads.PpsThread;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

public class Main {

    private static final SimpleDateFormat
            sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"),
            sdfFileSafe = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss");

    private static volatile boolean isOutgoingPcap = false;

                                            // default interface is ens3
    private static String ETH_INTERFACE = "ens3";
    private static final String
            PPS_FILE = "/proc/net/dev",
            TOTAL_INCOMING_BYTES_FILE = "/sys/class/net/%s/statistics/rx_bytes",
            PCAP_DIRECTORY = "./iDetection/pcaps/";

    public static volatile long peakPps = 0;
    public static volatile double peakMbps = 0;

    public static volatile long currentPps = 0;
    public static volatile double currentMbps = 0;
    private static volatile String lastPacketCapture = "N/A";


    public static void main(String[] args) {

        if (args.length == 0){
            System.out.println("Please enter your Network Interface.");
            return;
        }

        final File pcapDir = new File(PCAP_DIRECTORY);
        if (!pcapDir.isDirectory()) pcapDir.mkdirs();

        ETH_INTERFACE = args[0];
        getIpAddressByInterface(); // preload it
        new PpsThread().start();
        new MbpsThread().start();

        while(true){
            try {
                System.out.println(Colour.CLEAR.get());
                System.out.println();
                System.out.println();
                System.out.println(Colour.GREEN.get() + "   [Aaron Akhtar - " + Colour.YELLOW.get() + "iDetection" + Colour.GREEN.get() + "] " + Colour.YELLOW.get() + " ["+Colour.WHITE.get()+ sdf.format(new Date()) + Colour.YELLOW.get() + "]");
                System.out.println(Colour.GREEN.get() + "     {multi_threading=true, target_interface=" + ETH_INTERFACE + "}");
                System.out.println();
                System.out.println(Colour.WHITE.get() + "     ( Current Node Statistics )");
                System.out.println(Colour.YELLOW.get() + " [ Current MBPS -> " + Colour.WHITE.get() + currentMbps + "mbps" + Colour.YELLOW.get() + " ]");
                System.out.println(Colour.YELLOW.get() + " [ Current PPS -> " + Colour.WHITE.get() + currentPps + Colour.YELLOW.get() + " ]");
                System.out.println();
                System.out.println(Colour.WHITE.get() + "     ( Peak Node Statistics )");
                System.out.println(Colour.YELLOW.get() + " [ Peak MBPS -> " + Colour.WHITE.get() + peakMbps + "mbps" + Colour.YELLOW.get() + " ]");
                System.out.println(Colour.YELLOW.get() + " [ Peak PPS -> " + Colour.WHITE.get() + peakPps + Colour.YELLOW.get() + " ]");
                System.out.println();
                System.out.println(Colour.WHITE.get() + "     ( Node Information )");
                System.out.println(Colour.YELLOW.get() + " [ Node Status -> " + Colour.WHITE.get() + getSystemStatus() + Colour.YELLOW.get() + " (Calculated) ]");
                System.out.println(Colour.YELLOW.get() + " [ IP Address -> " + Colour.WHITE.get() + getIpAddressByInterface() + Colour.YELLOW.get() + " ]");
                System.out.println(Colour.YELLOW.get() + " [ Operating System -> " + Colour.WHITE.get() + System.getProperty("os.name") + Colour.YELLOW.get() + " ]");
                System.out.println(Colour.YELLOW.get() + " [ Running Under @ -> " + Colour.WHITE.get() + System.getProperty("user.name") + Colour.YELLOW.get() + " ]");
                System.out.println();
                System.out.println();
                System.out.println(Colour.MAGENTA.get() + " [ Currently Outgoing Capture -> " + Colour.WHITE.get() + isOutgoingPcap + Colour.MAGENTA.get() + " ]");
                System.out.println(Colour.MAGENTA.get() + " [ Last Packet Capture -> " + Colour.WHITE.get() + lastPacketCapture + Colour.MAGENTA.get() + " ]");
                System.out.println(Colour.RESET.get());
                Thread.sleep(60);
            }catch (Exception e){}
        }
    }

    private static String getSystemStatus(){
        String status = "Unable to Detect / Undefined";
        boolean capture = false;
        if ( currentMbps < 90 || (currentMbps < 100 && (currentPps < 500)) || (currentMbps < 50 && (currentPps > 500))){
            status = Colour.GREEN.get() + "Good";
        }else if ((currentMbps > 120 && currentMbps < 200 && (currentPps <= 1000)) || (currentMbps > 200 && currentMbps < 300 && (currentPps <= 500))
                                                                        || (currentMbps > 90 && currentMbps < 300)){
            status = Colour.YELLOW.get() + "Under Light Load";
        }else if ((currentMbps > 300 && currentMbps < 1000 && (currentPps > 1000))
                                                        || (currentMbps > 1000 && currentMbps < 2000)){
            status = Colour.YELLOW.get() + "Under Slightly Decent Load";
            capture = true;
        }else if ((currentMbps > 1000 && currentMbps < 3000 && (currentPps > 3000))
                                                            || (currentMbps > 1000 && currentMbps < 2000)){
            status = Colour.YELLOW.get() + "Under Decent Load";
            capture = true;
        }else if ((currentMbps > 2000 && currentMbps < 6500)){
            status = Colour.YELLOW.get() + "Under Medium Load";
            capture = true;
        }else if ((currentMbps > 6500)){
            status = Colour.RED.get() + "Under Heavy Network Load";
            capture = true;
        }else if ((currentPps > 10000)){
            status = Colour.YELLOW.get() + "Under Slight PPS Load";
            capture = true;
        }else if ((currentPps > 50000)){
            status =Colour.YELLOW.get() + "Under Decent PPS Load";
            capture = true;
        }else if ((currentPps > 100000)){
            status = Colour.RED.get() + "Under Heavy PPS Load";
            capture = true;
        }
        if (capture) {
            final Runnable runnable = executePacketCapture(30);
            if (runnable == null) return status;    // outgoing pcap currently
            new Thread(runnable).start();
        }
        return status;
    }

    public static long getCurrentIncomingPps(){
        try {
            final long pps1 = Long.parseLong(getTotalIncomingPackets());
            Thread.sleep(1000); // wait 1 second to get PER SECOND rate.
            return Long.parseLong(getTotalIncomingPackets()) - pps1;
        }catch (Exception e){}
        return 0;
    }

    public static double getCurrentIncomingMbps(){
        try{
            final long current_total_bytes = Long.parseLong(Files.readAllLines(Paths.get(new File(String.format(TOTAL_INCOMING_BYTES_FILE, ETH_INTERFACE)).toURI())).get(0));
            Thread.sleep(1000); // wait 1 second to get PER SECOND rate.
            final double current_mbps =
                    ((current_total_bytes - Long.parseLong(Files.readAllLines(Paths.get(new File(String.format(TOTAL_INCOMING_BYTES_FILE, ETH_INTERFACE)).toURI())).get(0))) / 125000) * (-1);
            return current_mbps;
        }catch (Exception e) {}
        return 0;
    }

    private static String getTotalIncomingPackets(){
        try{
            try(BufferedReader reader = new BufferedReader(new FileReader(PPS_FILE))){
                String s;
                while((s = reader.readLine()) != null){
                    if (s.contains(ETH_INTERFACE)){
                        return s.split(":")[1].split(" ")[2];
                    }
                }
            }
        }catch (Exception e){}
        return "ERR";
    }

                                                    // seconds
    private static Runnable executePacketCapture(int captureTime){
        if (isOutgoingPcap) return null;
        isOutgoingPcap = true;
        return new Runnable() {
            @Override
            public void run() {
                try{
                    final Date date = new Date();
                    final String f = sdfFileSafe.format(date) +".pcap";
                    Runtime.getRuntime().exec("timeout "+captureTime+" tcpdump -i " + ETH_INTERFACE + " -w "+PCAP_DIRECTORY+ f).waitFor();
                    lastPacketCapture = f + "  (Date of Capture: ["+sdf.format(date)+"])";
                    Thread.sleep(10000);    // 60 seconds after capturing packets, thread will kill itself and become available for another capture. (60s Between Each Capture MIN)
                }catch (Exception e){}
                isOutgoingPcap = false;
            }
        };
    }

    private static final String getIpAddressByInterface(){
        try {
            final Enumeration<InetAddress> inetAddress = NetworkInterface.getByName(ETH_INTERFACE).getInetAddresses();
            while (inetAddress.hasMoreElements()) {
                final InetAddress address = inetAddress.nextElement();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return address.getHostAddress();
                }
            }
        }catch (Exception e){}
        return "N/A";
    }



}
