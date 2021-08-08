package me.aaronakhtar.idetection.threads;

import me.aaronakhtar.idetection.Main;

public class MbpsThread extends Thread {

    @Override
    public void run() {
        while(true){
            try{
                Main.currentMbps = Main.getCurrentIncomingMbps();
                if (Main.currentMbps > Main.peakMbps) Main.peakMbps = Main.currentMbps;
                Thread.sleep(25);
            }catch (Exception e){}
        }
    }
}
