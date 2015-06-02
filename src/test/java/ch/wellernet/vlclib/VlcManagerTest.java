package ch.wellernet.vlclib;

import static ch.wellernet.vlclib.MediaType.BROADCAST;
import static ch.wellernet.vlclib.MediaType.SCHEDULE;
import static ch.wellernet.vlclib.MediaType.VOD;
import static ch.wellernet.vlclib.VlcManager.ANY_PROMPT;
import static ch.wellernet.vlclib.VlcManager.DEFAULT_HOSTANAME;
import static ch.wellernet.vlclib.VlcManager.DEFAULT_PORT;
import static ch.wellernet.vlclib.VlcManager.DISABLED;
import static ch.wellernet.vlclib.VlcManager.ENABLED;
import static ch.wellernet.vlclib.VlcManager.NORMAL_PROMPT;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.lang.System.arraycopy;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.repeat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyByte;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.telnet.TelnetClient;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class VlcManagerTest {

    private static final char[] PASSWORD = "secret".toCharArray();

    private static final String MEDIA_NAME = "channel42";

    private static final String MEDIA_ITEM_FILE_PATH_1 = "/home/myself/films/film1.avi";
    private static final Duration MEDIA_ITEM_LENGTH_1 = new Duration(669000000l);
    private static final String MEDIA_ITEM_FILE_PATH_2 = "/home/myself/films/film2.avi";

    private static final int STATE_PLAY_LIST_INDEX = 1;
    private static final float STATE_POSITION = 0.021375f;
    private static final boolean STATE_LOOP = true;

    private static final VlcInput INPUT = new VlcInput(MEDIA_ITEM_FILE_PATH_1);

    private static final VlcOutput OUTPUT = new VlcOutput.Builder().module("gather").module("std").property("access", "http").property("mux", "ps")
            .property("dst", ":8080").build();

    private static final float SEEK_PERCENTAGE_POSITION = .42f;
    private static final Duration SEEK_DURATION_POSITION = new Duration(42000000l);

    private static final String SAMPLE_COMMAND = "new channel42 broadcast enabled";

    private static final VlcOption OPTION_WITH_VALUE = new VlcOption("sout-display-delay", "150");
    private static final VlcOption OPTION_WITHOUT_VALUE = new VlcOption("sout-keep");

    private static final String EXPECTED_NEW_MEDIA_COMMAND = format("new %s %%s %%s", MEDIA_NAME);
    private static final String EXPECTED_DEL_MEDIA_COMMAND = format("del %s", MEDIA_NAME);
    private static final String EXPECTED_SETUP_OUTPUT_COMMAND = format("setup %s output %s", MEDIA_NAME, OUTPUT);
    private static final String EXPECTED_SETUP_INPUT_COMMAND = format("setup %s input %s", MEDIA_NAME, MEDIA_ITEM_FILE_PATH_1);
    private static final String EXPECTED_SETUP_INPUTDEL_ALL_COMMAND = format("setup %s inputdel all", MEDIA_NAME);
    private static final String EXPECTED_SETUP_OPTION_WITH_VALUE_COMMAND = format("setup %s option %s=%s", MEDIA_NAME, OPTION_WITH_VALUE.getName(),
            OPTION_WITH_VALUE.getValue());
    private static final String EXPECTED_SETUP_OPTION_WITHOUT_VALUE_COMMAND = format("setup %s option %s", MEDIA_NAME, OPTION_WITHOUT_VALUE.getName());
    private static final String EXPECTED_PLAY_COMMAND = format("control %s play", MEDIA_NAME);
    private static final String EXPECTED_PLAY_ITEM_COMMAND = format("control %s play %s", MEDIA_NAME, STATE_PLAY_LIST_INDEX);
    private static final String EXPECTED_SEEK_PERCENTAGE_COMMAND = format("control %s seek %f", MEDIA_NAME, SEEK_PERCENTAGE_POSITION);
    private static final String EXPECTED_SEEK_DURATION_COMMAND = format("control %s seek %dms", MEDIA_NAME, SEEK_DURATION_POSITION.getMillis());
    private static final String EXPECTED_STOP_COMMAND = format("control %s stop", MEDIA_NAME);
    private static final String EXPECTED_SHOW_COMMAND = format("show %s", MEDIA_NAME);

    private static final String WRONG_RESULT = "an error happend\n> ";

    // @formatter:off
    private static final String PLAYING_MEDIA_RESULT = format(""
            + "show\n"
            + "    channel1\n"
            + "        type : broadcast\n"
            + "        enabled : yes\n"
            + "        loop : %s\n"
            + "        inputs\n"
            + "            1 : %s\n"
            + "            2 : %s\n"
            + "        output : #gather:standard{access=http,mux=ps,dst=:8080/channel1}\n"
            + "        options\n"
            + "            sout-keep\n"
            + "        instances\n"
            + "            instance\n"
            + "                name : default\n"
            + "                state : playing\n"
            + "                position : %s\n"
            + "                time : 14300000\n"
            + "                length : %s\n"
            + "                rate : 1.000000\n"
            + "                title : 0\n"
            + "                chapter : 0\n"
            + "                can-seek : 1\n"
            + "                playlistindex : %s\n"
            + "> ", STATE_LOOP ? "yes" : "no", MEDIA_ITEM_FILE_PATH_1, MEDIA_ITEM_FILE_PATH_2, STATE_POSITION,
                    MEDIA_ITEM_LENGTH_1.getMillis(), STATE_PLAY_LIST_INDEX);
    // @formatter:on

    // under test
    @Spy
    @InjectMocks
    public VlcManager vlcManager;

    @Mock
    private TelnetClient telnetClient;

    @Mock
    private OutputStream outputStream;

    @Mock
    private InputStream inputStream;

    @Before
    public void setup() {
        initMocks(this);
        doReturn(inputStream).when(telnetClient).getInputStream();
        doReturn(outputStream).when(telnetClient).getOutputStream();
    }

    @Test
    public void shoudConnectAndLoginOnTelnet() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendPassword(any(char[].class));
        doReturn(null).when(vlcManager).waitForAndClear(any(Pattern.class));

        // when
        vlcManager.connect(PASSWORD.clone());

        // then
        InOrder order = inOrder(vlcManager, telnetClient);
        order.verify(telnetClient).connect(DEFAULT_HOSTANAME, DEFAULT_PORT);
        order.verify(vlcManager).waitForAndClear(VlcManager.PASSWORD_PROMPT);
        order.verify(vlcManager).sendPassword(PASSWORD.clone());
        order.verify(vlcManager).waitForAndClear(ANY_PROMPT);
    }

    @Test
    public void shouldAddInputItem() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.addInputItem(MEDIA_NAME, INPUT);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SETUP_INPUT_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test
    public void shouldBringMediaInPlayingState() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.play(MEDIA_NAME);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_PLAY_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test
    public void shouldBringMediaInPlayingWithPlayListIndex() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.play(MEDIA_NAME, STATE_PLAY_LIST_INDEX);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_PLAY_ITEM_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test
    public void shouldBringMediaInStoppedState() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.stop(MEDIA_NAME);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_STOP_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test
    public void shouldClearInput() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.clearInput(MEDIA_NAME);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SETUP_INPUTDEL_ALL_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test
    public void shouldCreateNewBroadcastMediaDisabled() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();
        VlcMedia media = new VlcMedia(MEDIA_NAME, BROADCAST, false, OUTPUT);

        // when
        vlcManager.createMedia(media);

        // then
        verifyCreateMedia(BROADCAST, false);
    }

    @Test
    public void shouldCreateNewBroadcastMediaEnabled() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();
        VlcMedia media = new VlcMedia(MEDIA_NAME, BROADCAST, true, OUTPUT);

        // when
        vlcManager.createMedia(media);

        // then
        verifyCreateMedia(BROADCAST, true);
    }

    @Test
    public void shouldCreateNewSchduleMediaEnabled() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();
        VlcMedia media = new VlcMedia(MEDIA_NAME, SCHEDULE, true, OUTPUT);

        // when
        vlcManager.createMedia(media);

        // then
        verifyCreateMedia(SCHEDULE, true);
    }

    @Test
    public void shouldCreateNewVodMedia() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();
        VlcMedia media = new VlcMedia(MEDIA_NAME, VOD, true, OUTPUT);

        // when
        vlcManager.createMedia(media);

        // then
        verifyCreateMedia(VOD, true);
    }

    @Test
    public void shouldDeleteMedia() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.deleteMedia(MEDIA_NAME);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_DEL_MEDIA_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test
    public void shouldReadCurrentLength() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(PLAYING_MEDIA_RESULT);

        // when
        Duration length = vlcManager.readCurrentLength(MEDIA_NAME);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SHOW_COMMAND);
        assertThat(length, is(MEDIA_ITEM_LENGTH_1));
    }

    @Test
    public void shouldReadCurrentPosition() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(PLAYING_MEDIA_RESULT);

        // when
        float position = vlcManager.readCurrentPosition(MEDIA_NAME);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SHOW_COMMAND);
        assertThat(position, is(STATE_POSITION));
    }

    @Test
    public <T> void shouldReadInputAndMatchTheWholeInputMessage() throws VlcConnectionException, IOException {
        // given
        mockInputStreamRead(PLAYING_MEDIA_RESULT);

        // when
        Matcher matcher = vlcManager.waitForAndClear(NORMAL_PROMPT);

        // then
        assertThat(matcher.group(0), is(PLAYING_MEDIA_RESULT));
    }

    @Test
    public void shouldReadLoopState() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(PLAYING_MEDIA_RESULT);

        // when
        boolean state = vlcManager.readLoopState(MEDIA_NAME);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SHOW_COMMAND);
        assertThat(state, is(STATE_LOOP));
    }

    @Test
    public void shouldReadPlayListIitems() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(PLAYING_MEDIA_RESULT);

        // when
        List<VlcInput> items = vlcManager.readPlayListItems(MEDIA_NAME);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SHOW_COMMAND);
        assertThat(items, is(asList(new VlcInput(MEDIA_ITEM_FILE_PATH_1), new VlcInput(MEDIA_ITEM_FILE_PATH_2))));
    }

    @Test
    public void shouldReadPlayListIndex() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(PLAYING_MEDIA_RESULT);

        // when
        int playListIndex = vlcManager.readPlayListIndex(MEDIA_NAME);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SHOW_COMMAND);
        assertThat(playListIndex, is(STATE_PLAY_LIST_INDEX));
    }

    @Test
    public void shouldRetrunEmptyListIfReadPlayListIndexFails() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(WRONG_RESULT);

        // when
        List<VlcInput> playListItems = vlcManager.readPlayListItems(MEDIA_NAME);

        // then
        assertThat(playListItems, is(Collections.<VlcInput> emptyList()));
    }

    @Test
    public void shouldRetrunNegativeValueIfReadCrrentPositionFails() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(WRONG_RESULT);

        // when
        float position = vlcManager.readCurrentPosition(MEDIA_NAME);

        // then
        assertThat(position, is(lessThan(.0f)));
    }

    @Test
    public void shouldRetrunNegativeValueIfReadPlayListIndexFails() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(WRONG_RESULT);

        // when
        int playListIndex = vlcManager.readPlayListIndex(MEDIA_NAME);

        // then
        assertThat(playListIndex, is(lessThan(0)));
    }

    @Test
    public void shouldRetrunNullIfReadCurrentLengthFails() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(WRONG_RESULT);

        // when
        Duration length = vlcManager.readCurrentLength(MEDIA_NAME);

        // then
        assertThat(length, is(nullValue()));
    }

    @Test
    public void shouldRetrunNullIfReadLoopStateFails() throws VlcConnectionException, IOException {
        // given
        doNothing().when(vlcManager).sendCommand(anyString());
        mockInputStreamRead(WRONG_RESULT);

        // when
        boolean state = vlcManager.readLoopState(MEDIA_NAME);

        // then
        assertThat(state, is(false));
    }

    @Test
    public void shouldSeekCurrentPlayedItemToGivenDuration() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.seek(MEDIA_NAME, SEEK_DURATION_POSITION);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SEEK_DURATION_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test
    public void shouldSeekCurrentPlayedItemToGivenPercentage() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.seek(MEDIA_NAME, SEEK_PERCENTAGE_POSITION);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SEEK_PERCENTAGE_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test()
    public void shouldSendAndWipePassword() throws VlcConnectionException, IOException {
        // given
        char[] password = PASSWORD.clone();

        // when
        vlcManager.sendPassword(password);

        // then
        InOrder order = inOrder(outputStream);
        for (char c : PASSWORD) {
            order.verify(outputStream).write(c);
        }
        order.verify(outputStream).write('\n');
        order.verify(outputStream).flush();

        // finally password should have been wiped to improve security
        assertThat(password, is(repeat('\0', PASSWORD.length).toCharArray()));
    }

    @Test()
    public void shouldSendCommand() throws VlcConnectionException, IOException {
        // given

        // when
        vlcManager.sendCommand(SAMPLE_COMMAND);

        // then
        InOrder order = inOrder(outputStream);
        order.verify(outputStream).write((SAMPLE_COMMAND + '\n').getBytes());
        order.verify(outputStream).flush();
    }

    @Test
    public void shouldSetupOptionWithoutValue() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.setupOption(MEDIA_NAME, OPTION_WITHOUT_VALUE);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SETUP_OPTION_WITHOUT_VALUE_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test
    public void shouldSetupOptionWithValue() throws VlcConnectionException {
        // given
        mockedBaseCommunicationMethods();

        // when
        vlcManager.setupOption(MEDIA_NAME, OPTION_WITH_VALUE);

        // then
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(EXPECTED_SETUP_OPTION_WITH_VALUE_COMMAND);
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
    }

    @Test(expected = VlcConnectionException.class)
    public void shouldThrowVlcConnectionExceptionWhenCatchingAnIOExceptionWhenSendingCommand() throws VlcConnectionException, IOException {
        // given
        doThrow(IOException.class).when(outputStream).write(any(byte[].class));

        // when
        vlcManager.sendCommand(SAMPLE_COMMAND);

        // then
        // a VlcConnectionException is expected
    }

    @Test(expected = VlcConnectionException.class)
    public void shouldThrowVlcConnectionExceptionWhenCatchingAnIOExceptionWhenSendingPassword() throws VlcConnectionException, IOException {
        // given
        doThrow(IOException.class).when(outputStream).write(anyByte());

        // when
        vlcManager.sendPassword(PASSWORD.clone());

        // then
        // a VlcConnectionException is expected
    }

    @Test(expected = VlcConnectionException.class)
    public void shouldThrowVlcConnectionExceptionWhenCatchingAnIOExceptionWhenWaitingForInputInput() throws VlcConnectionException, IOException {
        // given
        doThrow(IOException.class).when(inputStream).read(any(byte[].class));

        // when
        vlcManager.waitForAndClear(NORMAL_PROMPT);

        // then
        // a VlcConnectionException is expected
    }

    private void mockedBaseCommunicationMethods() throws VlcConnectionException {
        doReturn(null).when(vlcManager).waitForAndClear(any(Pattern.class));
        doNothing().when(vlcManager).sendCommand(anyString());
    }

    private void mockInputStreamRead(final String message) throws IOException {
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                byte[] buffer = invocation.getArgumentAt(0, byte[].class);
                arraycopy(message.getBytes(), 0, buffer, 0, min(message.length(), buffer.length));
                return min(message.length(), buffer.length);
            }
        }).when(inputStream).read(any(byte[].class));
    }

    private void verifyCreateMedia(MediaType type, boolean enabled) throws VlcConnectionException {
        InOrder order = inOrder(vlcManager);
        order.verify(vlcManager).sendCommand(format(EXPECTED_DEL_MEDIA_COMMAND, MEDIA_NAME));
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
        order.verify(vlcManager).sendCommand(format(EXPECTED_NEW_MEDIA_COMMAND, type.value(), enabled ? ENABLED : DISABLED));
        order.verify(vlcManager).waitForAndClear(NORMAL_PROMPT);
        order.verify(vlcManager).sendCommand(EXPECTED_SETUP_OUTPUT_COMMAND);
    }
}
