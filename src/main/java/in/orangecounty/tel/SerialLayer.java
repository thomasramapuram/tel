package in.orangecounty.tel;

/**
 * Created by thomasusual on 03-08-2015.
 */
public interface SerialLayer {
    void setDataLayer(DataLayer dataLayer);
    void sendMessage(byte[] message) throws Exception;
}
