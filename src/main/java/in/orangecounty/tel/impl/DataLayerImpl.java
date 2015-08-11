package in.orangecounty.tel.impl;

import in.orangecounty.tel.ProtocolLayerListener;
import in.orangecounty.tel.SerialLayer;
import in.orangecounty.tel.DataLayer;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * The DataLayer uses the SerailImpl as the physical layer to send and receive Messages.
 * The DataLayer is the 2nd layer.  It takes care of error correction using CRC and sends the necessary
 * Positive Acknowledgement (ACK) or Negative Acknowledgement(NAK)
 * <p/>
 * Created by jamsheer on 3/6/15.
 */
public class DataLayerImpl implements DataLayer {

    private static final Logger log = LoggerFactory.getLogger(DataLayerImpl.class);
    private static final String INIT = "\u0031\u0021\u0005";
    private static final byte ACK = 6;
    private static final byte STX = 2;
    private static final byte ENQ = 5;
    private static final byte NAK = 21;
    private static final byte EOT = 4;
    private static final byte DLE = 16;
    private static final byte ETX = 3;
    private ProtocolLayerListener appLayer;
    private Queue<String> buffer = new ConcurrentLinkedDeque<String>();
    private String messageToSend = null;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    ScheduledFuture initSentFuture;
    ScheduledFuture messageSentFuture;
    ScheduledFuture receivingFuture;
    private int messageCounter = 0;

    SerialLayer serialLayer;

    public void setSerialLayer(SerialLayer serialLayer) {
        this.serialLayer = serialLayer;
    }

    public void setAppLayer(ProtocolLayerListener appLayer) {
        this.appLayer = appLayer;
    }

    public void sendMessage(String message){
        log.debug("Send Message called with {}", message);
        buffer.add(message);
        sendIfClear();
    }

    private void sendIfClear(){
        log.debug("Send if Clear called.  Phase1 {}, Phase2 {}, Receiving {}", isPhaseOne(), isPhaseTwo(), isReceiving());
        if(!isPhaseOne()&& !isPhaseTwo() && !isReceiving()){
            sendInit();
            enterPhaseOne();
        }
    }

    @Override
    public void onMessage(final byte[] message) {
        log.debug("Received message {} converted it to {}", message, new String(message));
        log.debug("Checking message {} with {}", message, INIT.getBytes());
        if (Arrays.equals(message, INIT.getBytes())) {
            if(isPhaseOne()){
                exitPhaseOne();
                return;
            }
            sendACK();
            enterReceivingPhase();
            /* Start Timer 2-1 (35 Seconds) */
        } else if ((message[0] == STX) && (message[message.length - 2] == ETX)) {
            log.debug("Received a  message ");
            /*Check BCC and Send ACK/NAK and reset the receiving phase */
            byte[] payload = Arrays.copyOfRange(message, 1, message.length - 1);
            byte bcc = getBCC(payload);
            log.debug("Message BCC {} | Calculated BCC {}", message[message.length - 1], bcc);
            if (message[message.length - 1] == bcc) {
                log.info("Receive Message : {}", new String(payload));
                appLayer.onMessage(new String(Arrays.copyOfRange(message, 1, message.length - 2)));
                exitReceivingPhase();
                sendACK();
                enterReceivingPhase();
            } else {
                sendNAK();
            }
        } else if (Arrays.equals(message, new byte[]{EOT})) {
            /*//stop Timer 2-2 (32 Seconds)*/
            exitReceivingPhase();
            sendIfClear();
        } else if (Arrays.equals(message, new byte[]{ACK})) {
            if (isPhaseOne()) {
                byte[] msg = parseMessage(buffer.peek());
                /*//Stop Timer 1-1*/
                exitPhaseOne();
                sendMessage(msg);
                enterPhaseTwo();
                /*//Close Future, Schedule Message change Phase = 2*/
            } else if (isPhaseTwo()) {
                /*//Close Future, Send EOT change Phase = 0*/
                exitPhaseTwo();
                messageCounter = 0;
                String msg = buffer.poll();
                log.debug("Receivd Ack and removing Message : {} from buffer", msg);
                sendEOT();
                sendIfClear();
            } else {
                log.error("Received {}. Phase1 {}, Phase2 {}, Receiving {}", message, isPhaseOne(), isPhaseTwo(), isReceiving());

            }
        } else if (Arrays.equals(message, new byte[]{NAK})) {
            if (isPhaseTwo()) {
                if (messageCounter < 4) {
                    byte[] msg = parseMessage(messageToSend);
                    exitPhaseTwo();
                    sendMessage(msg);
                    enterPhaseTwo();
                } else {
                    exitPhaseTwo();
                    messageCounter = 0;
                    sendEOT();
                }
            }
        } else if (Arrays.equals(message, new byte[]{DLE, '<'})) {
            /*//Stop Timer 1-1*/
            exitPhaseOne();
            sendEOT();
        } else {
            log.debug("Received : {}", new String(message));
        }

    }

    private void enterReceivingPhase(){
        receivingFuture = scheduler.schedule(new Callable() {
            @Override
            public Object call() throws Exception {
                exitReceivingPhase();
                return null;
            }
        }, 35l, TimeUnit.SECONDS);
    }

    private void exitReceivingPhase() {
        receivingFuture.cancel(true);
    }

    private void sendInit() {
        try {
            serialLayer.sendMessage(INIT.getBytes());
        } catch (Exception e) {
            log.debug("IO Exception", e);
        }
    }

    private boolean isPhaseOne() {
        return initSentFuture == null || !initSentFuture.isDone();
    }

    private boolean isReceiving(){
        return receivingFuture == null || !receivingFuture.isDone();
    }

    private boolean isPhaseTwo() {
        return messageSentFuture == null || !messageSentFuture.isDone();
    }

    private void sendMessage(final byte[] message) {
        try {
            serialLayer.sendMessage(message);
        } catch (Exception e) {
            log.debug("Exception on Send Message", e);
        }
    }

    private byte[] parseMessage(final String message) {
        byte[] msg = ArrayUtils.add(message.getBytes(), ETX);
                /* Sent message */
        byte bcc = getBCC(msg);
        msg = ArrayUtils.add(msg, bcc);
        msg = ArrayUtils.add(msg, 0, STX);
        return msg;
    }

    private void enterPhaseOne() {
        initSentFuture = scheduler.scheduleAtFixedRate(new Runnable() {
            private int counter = 0;

            @Override
            public void run() {
                if (counter < 16) {
                /* Send init */
                    try {
                        log.info("inside sendInit retry:" + counter);
                        serialLayer.sendMessage(INIT.getBytes());
                        counter++;
                    } catch (Exception e) {
                        log.debug("IO Exception", e);
                    }
                } else {
                    sendEOT();
                    initSentFuture.cancel(true);
                }
            }
        }, 0l, 1l, TimeUnit.SECONDS);
    }

    private void enterPhaseTwo() {
        messageSentFuture = scheduler.scheduleAtFixedRate(new Runnable() {
            int currentCount = 0;

            @Override
            public void run() {
                if (currentCount < 32) {
                    sendENQ();
                } else {
                    exitPhaseTwo();
                    messageCounter = 0;
                    sendEOT();
                }
            }
        }, 1l, 1l, TimeUnit.SECONDS);
    }

    private void exitPhaseTwo() {
        messageSentFuture.cancel(false);
    }

    private void exitPhaseOne() {
        initSentFuture.cancel(false);
    }

    private void sendNAK() {
        try {
            serialLayer.sendMessage(new byte[]{NAK});
        } catch (Exception e) {
            log.warn("Exception on Send Message", e);
        }
    }

    private void sendACK() {
        try {
            serialLayer.sendMessage(new byte[]{ACK});
        } catch (Exception e) {
            log.warn("Exception on Send Message", e);
        }
    }

    private void sendEOT() {
        try {
            serialLayer.sendMessage(new byte[]{EOT});
        } catch (Exception e) {
            log.debug("Exception on Send Message", e);
        }
    }

    private void sendENQ() {
        try {
            serialLayer.sendMessage(new byte[]{ENQ});
        } catch (Exception e) {
            log.debug("Exception on Send Message", e);
        }
    }


    private byte getBCC(byte[] msg) {
        byte lrc = 0;
        for (byte aMsg : msg) {
            lrc = (byte) (lrc ^ aMsg);
        }
        return lrc;
    }
}
