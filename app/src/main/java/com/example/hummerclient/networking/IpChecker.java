package com.example.hummerclient.networking;

import com.example.hummerclient.ui.UIRunnerInterface;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class IpChecker extends Thread {
    private String ipAddress = "";
    private UIRunnerInterface uiRunner;

    public IpChecker(UIRunnerInterface obj) {
        this.uiRunner = obj;
    }


    public void run() {
        try {
            Document doc = Jsoup.connect("https://www.checkip.org").get();
            ipAddress = doc.getElementById("yourip").select("h1").first().select("span").text();
            if (this.uiRunner != null) {
                this.uiRunner.runOnUI();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            return;
        }
    }

    public String getIpAddress() {
        return ipAddress;
    }
}