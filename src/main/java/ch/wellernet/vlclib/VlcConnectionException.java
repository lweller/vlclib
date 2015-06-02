package ch.wellernet.vlclib;

/**
 * Exception that is thrown whenever a problem with communication on VLC media player is detected. In case a such exception occurs, it's undefined if
 * command has been partially executed or not at all. So VLC media player may be in initial state before command took place, in any intermediary state
 * or may be in expected final state. Causes fo such an exception could be some network issues or misfunction of VLC media player. It is not
 * recommended to continue to use an instance of {@link VlcManager} that has thrown a such exception.
 *
 * @author Lucien Weller <lucien@wellernet.ch>
 * @since 1.0.0
 */
public class VlcConnectionException extends Exception {

    private static final long serialVersionUID = 1L;

    public VlcConnectionException(String message) {
        super(message);
    }

    public VlcConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public VlcConnectionException(Throwable cause) {
        super(cause);
    }
}
