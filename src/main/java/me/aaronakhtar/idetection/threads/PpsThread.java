package me.aaronakhtar.idetection.threads;

import me.aaronakhtar.idetection.Main;

public class PpsThread extends Thread {

    @Override
    public void run() {
        while(true){
            try{
                Main.currentPps = Main.getCurrentIncomingPps();
                if (Main.currentPps > Main.peakPps) Main.peakPps = Main.currentPps;
                Thread.sleep(25);
            }catch (Exception e){}
        }
    }
}
