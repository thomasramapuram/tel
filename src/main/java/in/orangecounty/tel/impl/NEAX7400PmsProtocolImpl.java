package in.orangecounty.tel.impl;

import in.orangecounty.tel.DataLayer;
import in.orangecounty.tel.ProtocolLayerListener;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * PMS Protocol
 * Created by jamsheer on 3/16/15.
 */
public class NEAX7400PmsProtocolImpl implements ProtocolLayerListener {
    private static final Logger log = LoggerFactory.getLogger(NEAX7400PmsProtocolImpl.class);
    private static final String STATUS_ENQUIRY = "1!L7007F  ";
    private static final int NAME_LEN = 15;
    private static final int EXT_LEN = 4;
    DataLayer dataLayer;

    ScheduledExecutorService scheduler;
    ScheduledFuture statusFuture;

    public void checkIn(String guestName, String extension) {
        setRestriction(extension, "0");
        setName(extension, guestName);
    }

    public void checkOut(String extension) {
        setRestriction(extension, "1");
        setName(extension, " ");
    }


    private void setExtensionProperies(Map<Long, Map<String, String>> extensions) {
//        System.out.println("\n\n extensions  --- "+extensions);
        String restrictionLevel = "0";
        String restrictionStatus = "";
        for (Map.Entry<Long, Map<String, String>> outerEntry : extensions.entrySet()) {
            for (Map.Entry<String, String> innerEntry : outerEntry.getValue().entrySet()) {

                restrictionStatus = innerEntry.getKey();
                if (innerEntry.getKey().trim().equals("true")) {
                    restrictionLevel = "0";
                } else if (restrictionStatus.trim().equals("false")) {
                    restrictionLevel = "1";
                }
                setName(outerEntry.getKey().toString(), innerEntry.getValue());
                setRestriction(outerEntry.getKey().toString(), restrictionLevel);
            }
        }
    }


    /* 0 - No restriction
    *  1 - Outward Restriction */
    public void setRestriction(String extension, String status) {
        log.info("Set restriction " + extension + " to " + status);
        StringBuilder sb = new StringBuilder("1!L15141");
        String ext = modifyExtension(extension);
        String st = status.trim();
        sb.append(fixWidth(extension, EXT_LEN));
        sb.append("  ");
        sb.append(st);
        sb.append("  ");
        sendMessage(sb.toString());
    }

    public void setName(String extension, String name) {
        log.info("set name " + extension + " to " + name);
        String extensionName = modifyName(name);
        String ext = modifyExtension(extension);
        StringBuilder sb = new StringBuilder("1!L21266");
        sb.append(ext);
        sb.append("  ");
        sb.append(extensionName);
        sendMessage(sb.toString());
    }

    private String modifyExtension(String extension) {
        return fixWidth(extension, 4);
    }

    private String modifyName(String name) {
        return fixWidth(name, 15);
    }

    private String fixWidth(String string, int width){
        return StringUtils.left(StringUtils.rightPad(string, width), width);
    }

    private void sendMessage(String message) {
        if (dataLayer != null) {
            dataLayer.sendMessage(message);
        } else {
            log.warn("Data Layer not Set. Could not send Message : " + message);
        }
    }

    public void start() {
        scheduler = Executors.newScheduledThreadPool(1);
        statusFuture = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                sendMessage(STATUS_ENQUIRY);
            }
        }, 1l, 1l, TimeUnit.MINUTES);
    }

    public void stop() {
        if (statusFuture != null) {
            statusFuture.cancel(true);
            statusFuture = null;
            scheduler = null;
        }
    }

    public Map<String, Map<String, String>> parseCallDetails(String message) {
        Calendar cal = Calendar.getInstance();
        Map<String, Map<String, String>> map = new HashMap<String, Map<String, String>>();
        Map<String, String> callDetailsMap = new HashMap<String, String>();
        String startTime = "";
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(message.substring(28, 30)));
        cal.set(Calendar.MINUTE, Integer.parseInt(message.substring(30, 32)));
        cal.set(Calendar.SECOND, Integer.parseInt(message.substring(32, 34)));
        cal.set(Calendar.MILLISECOND, 0);

        String stationNumber, routeNumber, trunkNumber, subscriberNumber, hour, minute, second, duration;
        stationNumber = message.substring(0, 4);
        routeNumber = message.substring(6, 9);
        trunkNumber = message.substring(9, 12);
        subscriberNumber = message.substring(12, 28);
        hour = message.substring(28, 30);
        minute = message.substring(30, 32);
        second = message.substring(32, 34);
        duration = message.substring(34, 39);

        startTime = message.substring(28, 30) + ":" + message.substring(30, 32) + ":" + message.substring(32, 34);

        if (Calendar.getInstance().getTime().before(cal.getTime())) {
            cal.add(Calendar.DATE, -1);
        }


        callDetailsMap.put("CALLED_NO", subscriberNumber);

        callDetailsMap.put("START_TIME", startTime);

        callDetailsMap.put("CALL_DURATION", duration);
        callDetailsMap.put("DATE_OF_CALL", cal.getTime().toString() + " " + startTime);

        map.put(stationNumber, callDetailsMap);

//        System.out.println("Station Number"+stationNumber);
//        System.out.println("Route Number"+routeNumber);
//        System.out.println("Trunk Number"+trunkNumber);
//        System.out.println("Subscriber Number"+subscriberNumber);
//        System.out.println("Hour"+hour);
//        System.out.println("Minute"+minute);
//        System.out.println("Second"+second);
//        System.out.println("Duration"+duration);
//        System.out.println("Start Date "+cal.getTime());
//
//        System.out.println("Modified date "+cal.getTime());
//        System.out.println("Current date "+new Date());
//        System.out.println("Compare "+cal.getTime().equals(new Date()));


        return map;


    }


    @Override
    public void onMessage(String s) {
        log.debug(s);
    }
}
