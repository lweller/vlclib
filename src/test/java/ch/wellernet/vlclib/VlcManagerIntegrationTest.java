package ch.wellernet.vlclib;

import static ch.wellernet.vlclib.MediaType.BROADCAST;
import static java.lang.Runtime.getRuntime;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class VlcManagerIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String VLC_EXEC_COMAND = "C:/Program Files (x86)/VideoLAN/VLC/vlc.exe -I telnet --telnet-password=" + PASSWORD;
    private static final String CHANNEL_NAME = "channel42";

    private Process vlcProcess;

    // class under test
    private VlcManager vlcManager;

    @Before
    public void setup() throws IOException {
        vlcProcess = getRuntime().exec(VLC_EXEC_COMAND);
        vlcManager = new VlcManager();
    }

    @Test
    public synchronized void shouldCreateANewBroadcastMedia() throws VlcConnectionException {
        // given
        VlcMedia media = new VlcMedia(CHANNEL_NAME, BROADCAST, true, null);

        // when
        vlcManager.connect(PASSWORD.toCharArray());
        vlcManager.createMedia(media);

        // then
        // TODO: check output
    }

    @After
    public void teardown() throws Throwable {
        if (vlcProcess != null) {
            vlcProcess.destroy();
            vlcProcess = null;
        }
    }
}
