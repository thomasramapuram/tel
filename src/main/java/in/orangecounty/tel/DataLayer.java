package in.orangecounty.tel;

/**
 * Created by thomas on 6/3/15.
 */
public interface DataLayer {
    void onMessage(final byte[] message);
    void sendMessage(String message) throws RuntimeException;
}
