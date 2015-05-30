package ch.wellernet.vlclib;

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
