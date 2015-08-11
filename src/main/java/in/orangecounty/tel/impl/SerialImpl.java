package in.orangecounty.tel.impl;

import gnu.io.*;
import in.orangecounty.tel.SerialLayer;
import in.orangecounty.tel.DataLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.TooManyListenersException;

/**
 * Implementation of Serial Layer
 * Created by thomas on 6/3/15.
 */
public class SerialImpl implements SerialLayer {
    private static final Logger log = LoggerFactory.getLogger(SerialImpl.class);
    private DataLayer dataLayer;
    private InputStream inputStream;
    private OutputStream outputStream;
    private SerialPort serialPort;

    @Override
    public void setDataLayer(DataLayer dataLayer) {
        this.dataLayer = dataLayer;
    }


    public void start() throws IOException {
        if (serialPort != null) {
            throw new IOException("Start has been called already: serialPort Not null");
        }
        Map<String, String> env = System.getenv();
        final String envVarName="SERIAL_PORT_NAME";
        String portName = "/dev/ttyS0";
        if(env.get(envVarName)!=null){
            portName = env.get(envVarName);
        }
        this.connect(portName);
    }

    public void stop() {
        if (serialPort != null) {
            try {
                /* close the i/o streams.  */
                outputStream.close();
                inputStream.close();
            } catch (IOException ex) {
            /*    don't care */
            }
            /* Close the port. */
            serialPort.close();
            serialPort = null;
        }
    }

    public void listPorts() {
        Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();
        while (portEnum.hasMoreElements()){
            CommPortIdentifier portIdentifier = (CommPortIdentifier) portEnum.nextElement();
            log.debug("Name: {} | Type: {}", portIdentifier.getName(), portIdentifier.getPortType());
        }
    }

    @Override
    public void sendMessage(byte[] message) throws Exception {
        if (outputStream != null) {
            log.debug("Sending Message : {}", message);
            outputStream.write(message);
            log.debug("Sending Done");
        } else {
            throw new IOException("No Output Stream.  Call start() before calling sendMessage()");
        }

    }

    private void connect(String portName) throws IOException {
//        portName = "/dev/ttyUSB0";
        log.debug("PortName: {}", portName);
        try {
            /*  Obtain a CommPortIdentifier object for the port you want to open */
            CommPortIdentifier portId =
                    CommPortIdentifier.getPortIdentifier(portName);

            /*  Get the port's ownership */
            serialPort = (SerialPort) portId.open("Demo application", 5000);

            /*  Set the parameters of the connection. */
            setSerialPortParameters();

            /* Open the input and output streams for the connection.
             If they won't open, close the port before throwing an
             exception. */
            outputStream = serialPort.getOutputStream();
            inputStream = serialPort.getInputStream();
            serialPort.addEventListener(new SerialPortEventListener() {
                @Override
                public void serialEvent(SerialPortEvent serialPortEvent) {
                    switch (serialPortEvent.getEventType()) {
                        case SerialPortEvent.DATA_AVAILABLE:
                            byte[] readBuffer = new byte[400];
                            try {
                                Thread.sleep(600);
                                int bytesAvailable = inputStream.available();
                                if (bytesAvailable > 0) {
                                    /* Read the serial port */
                                    int bytesRead = inputStream.read(readBuffer, 0, bytesAvailable);
                                    if(bytesRead != bytesAvailable){
                                        log.debug("Bytes read {} not equal to Bytes available {}", bytesRead, bytesAvailable);
                                    }
                                    byte[] readCopy = Arrays.copyOfRange(readBuffer,0, bytesAvailable);

                                    /* Print it out */
                                    log.debug("Received : {} coverted to {}", readCopy, new String(readCopy));
                                    if(dataLayer !=null){
                                        dataLayer.onMessage(readCopy);
                                    }
                                }
                            } catch (IOException e) {
                                log.warn("IOException : ", e);
                            } catch (InterruptedException e) {
                                log.warn("InterruptedException: ", e);
                            }
                    }
                }
            });
            serialPort.notifyOnDataAvailable(true);
        } catch (NoSuchPortException e) {
            log.error("Exception ", e);
            throw new IOException(e.getMessage());
        } catch (PortInUseException e) {
            log.error("Exception ", e);
            throw new IOException(e.getMessage());
        } catch (TooManyListenersException e) {
            log.error("Exception ", e);
            throw new IOException(e.getMessage());
        } catch (IOException e) {
            log.error("Exception ", e);
            serialPort.close();
            throw e;
        }
    }

    private void setSerialPortParameters() throws IOException {

        final int baudRate = 1200; /* 57600bps */

        try {
            /*  Set serial port to 57600bps-8N1..my favourite */
            serialPort.setSerialPortParams(
                    baudRate,
                    SerialPort.DATABITS_7,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_EVEN);

            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
        } catch (UnsupportedCommOperationException ex) {
            throw new IOException("Unsupported serial port parameter");
        }
    }
}
