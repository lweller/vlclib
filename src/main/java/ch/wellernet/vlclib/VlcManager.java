package ch.wellernet.vlclib;

import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.util.Collections.unmodifiableList;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.MULTILINE;
import static java.util.regex.Pattern.compile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.telnet.TelnetClient;
import org.joda.time.Duration;

/**
 * API for telnet interface of VLC media player (see <a href="http://www.videolan.org/vlc">http://www.videolan.org/vlc</a>).
 *
 * @author Lucien Weller <lucien@wellernet.ch>
 * @since 1.0.0
 */
public class VlcManager {

    private static final Log LOG = LogFactory.getLog(VlcManager.class);

    public static final String DEFAULT_HOSTANAME = "localhost";
    public static final int DEFAULT_PORT = 4212;

    private static final String PASSWORD_PROMPT_TEMPLATE = "^%s\nPassword: $";
    private static final String NORMAL_PROMPT_TEMPLATE = "^%s\n> $";

    static final Pattern PASSWORD_PROMPT = compile(format(PASSWORD_PROMPT_TEMPLATE, ".*?"), DOTALL);
    static final Pattern NORMAL_PROMPT = compile(format(NORMAL_PROMPT_TEMPLATE, ".*?"), DOTALL);
    static final Pattern ANY_PROMPT = compile(format("(%s)|(%s)", PASSWORD_PROMPT.pattern(), NORMAL_PROMPT.pattern()), DOTALL);

    static final String ENABLED = "enabled";
    static final String DISABLED = "disabled";

    private static final String COMMANDE_NEW = "new %s %s %s";
    private static final String COMMAND_SETUP_INPUT = "setup %s input %s";
    private static final String COMMAND_SETUP_INPUTDEL = "setup %s inputdel %s";
    private static final String COMMAND_SETUP_INPUTDELN = "setup %s inputdeln %d";
    private static final String COMMAND_SETUP_OUTPUT = "setup %s output %s";
    private static final String COMMAND_SETUP_OPTION = "setup %s option %s";
    private static final String COMMAND_DEL = "del %s";
    private static final String COMMAND_PLAY = "control %s play";
    private static final String COMMAND_PLAY_ITEM = COMMAND_PLAY + " %s";
    private static final String COMMAND_SEEK_PERCENTAGE = "control %s seek %f";
    private static final String COMMAND_SEEK_DURATION = "control %s seek %dms";
    private static final String COMMAND_STOP = "control %s stop";
    private static final String COMMAND_SHOW = "show %s";
    private static final String COMMAND_LOOP = "loop %s";
    private static final String COMMAND_UNLOOP = "unloop %s";

    // @formatter:off
    private static final Pattern COMMAND_SHOW_INPUTS = compile(""
            + "(show\n"
            + ".*?"
            + "        inputs\n"
            + "((            \\d+ : .*?\n)*)"
            + ".*?)|(.*?)", DOTALL);
    // @formatter:on
    private static final Pattern COMMAND_SHOW_INPUTS_SINGLE_INPUT = compile("^ {12}\\d : (.*)$", MULTILINE);
    private static final int COMMAND_SHOW_INPUTS_RESULT_START_GROUP = 2;

    // @formatter:off
    private static final Pattern COMMAND_SHOW_LOOP = compile(""
            + "(show\n"
            + ".*?"
            + "        loop : (no|yes)\n"
            + ".*?)|(.*?)", DOTALL);
    // @formatter:on
    private static final int COMMAND_SHOW_LOOP_RESULT_START_GROUP = 2;

    // @formatter:off
    private static final String COMMAND_SHOW_FEEDBACK_INSTANCES_TEMPLATE = ""
            + "(show\n"
            + ".*?"
            + "        instances\n"
            + "            instance\n"
            + "                name : default\n"
            + ".*?"
            + ".*?%s"
            + ".*?)|(.*?)";
    // @formatter:on

    private static final Pattern COMMAND_SHOW_PLAY_LIST_ITEM = compile(format(COMMAND_SHOW_FEEDBACK_INSTANCES_TEMPLATE, "playlistindex : (\\d+)\n"),
            DOTALL);
    private static final int COMMAND_SHOW_PLAY_LIST_ITEM_RESULT_GROUP = 2;

    private static final Pattern COMMAND_SHOW_CURRENT_POSITION = compile(
            format(COMMAND_SHOW_FEEDBACK_INSTANCES_TEMPLATE, "position : (\\d\\.\\d+)\n"), DOTALL);
    private static final int COMMAND_SHOW_CURRENT_POSITION_RESULT_GROUP = 2;

    private static final Pattern COMMAND_SHOW_CURRENT_LENGTH = compile(format(COMMAND_SHOW_FEEDBACK_INSTANCES_TEMPLATE, "length : (\\d+)\n"), DOTALL);
    private static final int COMMAND_SHOW_CURRENT_LENGTH_RESULT_GROUP = 2;

    private final String hostname;
    private final int port;

    private TelnetClient telnetClient;
    private String currentMessage;

    /**
     * Prepares a new instance for default host name and port (localhost:4212) but does not immediately connect.
     */
    public VlcManager() {
        this(DEFAULT_HOSTANAME, DEFAULT_PORT);
    }

    /**
     * Prepares a new instance but does not immediately connect.
     *
     * @param hostname
     *            host name to where VLC is running
     * @param port
     *            port on which VLC is listening for telnet connection
     */
    public VlcManager(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
        this.telnetClient = new TelnetClient();
        this.currentMessage = "";
        LOG.debug(format("created new instance for %s:%s", hostname, port));
    }

    /**
     * Appends a new multimedia item to play list of a existing media. If media does not exists, nothing will be done.
     *
     * @param mediaName
     *            name of media to which the item will be appended
     * @param input
     *            multimedia item to add
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void addInputItem(String mediaName, VlcInput input) throws VlcConnectionException {
        sendCommand(format(COMMAND_SETUP_INPUT, mediaName, input));
        waitForAndClear(NORMAL_PROMPT);
        LOG.debug(format("added input %s to media %s", input, mediaName));
    }

    /**
     * Clears the all multiemedia items of play list of media. If media is currently playing an item, it will continue until end of this item.
     *
     * @param mediaName
     *            name of media whose input will be cleared
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void clearInput(String mediaName) throws VlcConnectionException {
        sendCommand(format(COMMAND_SETUP_INPUTDEL, mediaName, "all"));
        waitForAndClear(NORMAL_PROMPT);
        LOG.debug(format("cleared input for media %s", mediaName));
    }

    /**
     * Opens a telnet connection to VLC and logs in.
     *
     * @param password
     *            password for telnet connection (will be wiped after login for security reasons)
     * @throws VlcConnectionException
     *             when connection can't be established (see cause for detailed reason)
     */
    public void connect(char[] password) throws VlcConnectionException {
        currentMessage = "";
        telnetClient.setReaderThread(true);
        try {
            telnetClient.connect(hostname, port);
            waitForAndClear(PASSWORD_PROMPT);
            sendPassword(password);
            waitForAndClear(ANY_PROMPT);
            LOG.debug(format("connected successfully to %s:%s", hostname, port));
        } catch (IOException exception) {
            LOG.warn(format("caught exception while connecting to %s%:%s", hostname, port), exception);
            throw new VlcConnectionException(exception);
        }
    }

    /**
     * Creates a new media in VLC. If a media with this name already exists, it will be replaced by a new one.
     *
     * @param name
     *            media name
     * @param type
     *            type of media
     * @param enabled
     *            weather to create in enabled or disabled state
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void createMedia(VlcMedia media) throws VlcConnectionException {
        deleteMedia(media.getName());
        sendCommand(format(COMMANDE_NEW, media.getName(), media.getType().value(), media.isEnabed() ? ENABLED : DISABLED));
        waitForAndClear(NORMAL_PROMPT);
        sendCommand(format(COMMAND_SETUP_OUTPUT, media.getName(), media.getOutput()));
        waitForAndClear(NORMAL_PROMPT);
        for (VlcOption option : media.getOptions()) {
            setupOption(media.getName(), option);
        }
        LOG.debug(format("created new media %s", media));
    }

    /**
     * Deletes the media in VLC. If it's currently playing, streaming will immediately be stopped.
     *
     * @param mediaName
     *            name of media that will be deleted
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void deleteMedia(String mediaName) throws VlcConnectionException {
        sendCommand(format(COMMAND_DEL, mediaName));
        waitForAndClear(NORMAL_PROMPT);
        LOG.debug(format("deleted media %s", mediaName));
    }

    /**
     * Closes the telnet connection to VLC.
     *
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void disconnect() throws VlcConnectionException {
        try {
            telnetClient.disconnect();
            LOG.debug(format("successfully disconnected from %s:%s", hostname, port));
        } catch (IOException exception) {
            LOG.warn(format("caught exception while disconnecting form %s:s)", hostname, port), exception);
            throw new VlcConnectionException(exception);
        }
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    /**
     * Brings a media in playing state. If the media is already playing command will have no effect.
     *
     * @param mediaName
     *            name of media which should start playing
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void play(String mediaName) throws VlcConnectionException {
        sendCommand(format(COMMAND_PLAY, mediaName));
        waitForAndClear(NORMAL_PROMPT);
        LOG.debug(format("media %s is now playing", mediaName));
    }

    /**
     * Brings a media in playing state for given item. If the media is already playing the item command will have no effect.
     *
     * @param mediaName
     *            name of media which should start playing
     * @param playListIndex
     *            index (starting form 1) of item to play
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void play(String mediaName, int playListIndex) throws VlcConnectionException {
        sendCommand(format(COMMAND_PLAY_ITEM, mediaName, playListIndex));
        waitForAndClear(NORMAL_PROMPT);
        LOG.debug(format("media %s is now playing item %s", mediaName, playListIndex));
    }

    /**
     * Retrieves the length of currently played item of a given media. If media is currently in stopped state or length can't be read, a
     * <code>null</code> will be returned.
     *
     * @param mediaName
     *            name of media to which should start playing
     * @return length of currently play item
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public Duration readCurrentLength(String mediaName) throws VlcConnectionException {
        sendCommand(format(COMMAND_SHOW, mediaName));
        Matcher matcher = waitForAndClear(COMMAND_SHOW_CURRENT_LENGTH);
        String result = matcher.group(COMMAND_SHOW_CURRENT_LENGTH_RESULT_GROUP);
        LOG.debug(format("length of currently played item on media %s is %s", mediaName, result));
        return result == null ? null : new Duration(parseLong(result));
    }

    /**
     * Retrieves the relative position of currently played item of a given media, where 0 is the start position and 1 the end position . If media is
     * currently in stopped state or position can't be read, a negative value will be returned.
     *
     * @param mediaName
     *            name of media to which should start playing
     * @return current position of played index
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public float readCurrentPosition(String mediaName) throws VlcConnectionException {
        sendCommand(format(COMMAND_SHOW, mediaName));
        Matcher matcher = waitForAndClear(COMMAND_SHOW_CURRENT_POSITION);
        String result = matcher.group(COMMAND_SHOW_CURRENT_POSITION_RESULT_GROUP);
        LOG.debug(format("position of currently played item on media %s is %s ms", mediaName, result));
        return result == null ? -1 : parseFloat(result);
    }

    /**
     * Retrieves weather a given media is looping or not . If media is currently in stopped state or state can't be read, <code>false</code> will be
     * returned.
     *
     * @param mediaName
     *            name of media to check for looping
     * @return <code>true</code> if media is looping, <code>false</code> in all other cases
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public boolean readLoopState(String mediaName) throws VlcConnectionException {
        sendCommand(format(COMMAND_SHOW, mediaName));
        Matcher matcher = waitForAndClear(COMMAND_SHOW_LOOP);
        String result = matcher.group(COMMAND_SHOW_LOOP_RESULT_START_GROUP);
        LOG.debug(format("loop state of media %s is %s", mediaName, result));
        return "yes".equals(result) ? true : false;
    }

    /**
     * Retrieves the index of currently played item of a given media. If media is currently in stopped state or index can't be read, a negative value
     * will be returned.
     *
     * @param mediaName
     *            name of media to which should start playing
     * @return current play list index
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public int readPlayListIndex(String mediaName) throws VlcConnectionException {
        sendCommand(format(COMMAND_SHOW, mediaName));
        Matcher matcher = waitForAndClear(COMMAND_SHOW_PLAY_LIST_ITEM);
        String result = matcher.group(COMMAND_SHOW_PLAY_LIST_ITEM_RESULT_GROUP);
        LOG.debug(format("media %s is currently playing item at index %s", mediaName, result));
        return result == null ? -1 : parseInt(result);
    }

    /**
     * Retrieves the input items currently queued for this media. If media is currently in stopped state or input can't be read, a empty list will be
     * returned.
     *
     * @param mediaName
     *            name of media to retrieve play list for
     * @return a list with {@link VlcInput} items currently queued
     * @throws VlcConnectionException
     */
    public List<VlcInput> readPlayListItems(String mediaName) throws VlcConnectionException {
        sendCommand(format(COMMAND_SHOW, mediaName));
        Matcher matcher = waitForAndClear(COMMAND_SHOW_INPUTS);
        String result = matcher.group(COMMAND_SHOW_INPUTS_RESULT_START_GROUP);
        List<VlcInput> resultList = new ArrayList<VlcInput>();
        if (result != null) {
            Matcher inputMatcher = COMMAND_SHOW_INPUTS_SINGLE_INPUT.matcher(result);
            while (inputMatcher.find()) {
                resultList.add(new VlcInput(inputMatcher.group(1)));
            }
        }
        LOG.debug(format("input of media %s is %s", mediaName, resultList));
        return unmodifiableList(resultList);
    }

    /**
     * Removes a multimedia item from media. If media does not exists or has no such item, nothing will be done.
     *
     * @param mediaName
     *            name of media from which the item will be removed
     * @param playListIndex
     *            index (starting form 1) of item to remove
     *
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void removeInputItem(String mediaName, int playListIndex) throws VlcConnectionException {
        sendCommand(format(COMMAND_SETUP_INPUTDELN, mediaName, playListIndex));
        waitForAndClear(NORMAL_PROMPT);
        LOG.debug(format("removed input %d of media %s", playListIndex, mediaName));
    }

    /**
     * Continues playing current item at given absolute position, which may be before or after current position. If the media is stopped command will
     * have no effect.
     *
     * @param mediaName
     *            name of media which should be seeked
     * @param position
     *            absolute position (duration from start of item)
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void seek(String mediaName, Duration position) throws VlcConnectionException {
        sendCommand(format(COMMAND_SEEK_DURATION, mediaName, position.getMillis()));
        waitForAndClear(NORMAL_PROMPT);
        LOG.debug(format("media %s seeked to absolute position %s ms", mediaName, position.getMillis()));
    }

    /**
     * Continues playing current item at given percentual position, which may be before or after current position. If the media is stopped command
     * will have no effect.
     *
     * @param mediaName
     *            name of media which should be seeked
     * @param position
     *            pencentual position
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void seek(String mediaName, float position) throws VlcConnectionException {
        sendCommand(format(COMMAND_SEEK_PERCENTAGE, mediaName, position));
        waitForAndClear(NORMAL_PROMPT);
        LOG.debug(format("media %s seeked to relative position %.2f %%", mediaName, position * 100));
    }

    /**
     * Sets an option for a given media.
     *
     * @param mediaName
     *            name of media to set option for
     * @param option
     *            the option to set
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void setupOption(String mediaName, VlcOption option) throws VlcConnectionException {
        sendCommand(format(COMMAND_SETUP_OPTION, mediaName, option));
        waitForAndClear(NORMAL_PROMPT);
    }

    /**
     * Brings a media in stopped state. If the media is already stopped command will have no effect.
     *
     * @param mediaName
     *            name of media to which should be stopped
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void stop(String mediaName) throws VlcConnectionException {
        sendCommand(format(COMMAND_STOP, mediaName));
        waitForAndClear(NORMAL_PROMPT);
        LOG.debug(format("stopped media %s", mediaName));
    }

    /**
     * Toggle the loop state of a media (starts looping if currently not looping and vice versa).
     *
     * @param mediaName
     *            name of media to toggle the loop state
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC (see cause for detailed reason)
     */
    public void toggleLoopState(String mediaName) throws VlcConnectionException {
        if (readLoopState(mediaName)) {
            sendCommand(format(COMMAND_LOOP, mediaName));
            LOG.debug(format("media %s is now looping", mediaName));
        } else {
            sendCommand(format(COMMAND_UNLOOP, mediaName));
            LOG.debug(format("media %s is not looping anymore", mediaName));
        }
        waitForAndClear(NORMAL_PROMPT);
    }

    /**
     * Send a command with terminating new line character if not already present.
     *
     * @param command
     *            command to send to telnet
     * @throws VlcConnectionException
     *             when there is a problem while writing output (see cause for detailed reason)
     */
    void sendCommand(String command) throws VlcConnectionException {
        try {
            OutputStream outputStream = telnetClient.getOutputStream();
            if (!command.endsWith("\n")) {
                command += "\n";
            }
            outputStream.write(command.getBytes());
            outputStream.flush();
            LOG.trace(format("sent telnet command: %s", command));
        } catch (IOException exception) {
            LOG.warn(format("caught exception while sending telnet command: %s", command), exception);
            throw new VlcConnectionException(exception);
        }
    }

    /**
     * Special method to send password given as char array for security reasons. A string would remain in memory until garbage collector flushes it.
     * char array will be wiped immediately after sending.
     *
     * @param password
     *            password for telnet connection (will be wiped after login for security reasons)
     * @throws VlcConnectionException
     *             when there is a problem while writing output (see cause for detailed reason)
     */
    void sendPassword(char[] password) throws VlcConnectionException {
        try {
            OutputStream outputStream = telnetClient.getOutputStream();
            for (int i = 0; i < password.length; i++) {
                outputStream.write(password[i]);
                password[i] = '\0';
            }
            outputStream.write('\n');
            outputStream.flush();
        } catch (IOException exception) {
            LOG.warn("caught exception while sending password to telnet", exception);
            throw new VlcConnectionException(exception);
        }
    }

    /**
     * Reads input from VLC until a given char sequence (for example a prompt) in form of a regular expression is found and clears input buffer until
     * and including the expected sequence.
     *
     * @param expectedMessage
     *            regular expression to find
     * @return the matcher that successfully found the expected message
     * @throws VlcConnectionException
     *             when there is a problem with the connection with VLC while waiting or reading input (see cause for detailed reason)
     */
    Matcher waitForAndClear(Pattern expectedMessage) throws VlcConnectionException {
        try {
            Matcher matcher;
            do {
                InputStream inputStream = telnetClient.getInputStream();
                byte[] buffer = new byte[1024];
                int length = inputStream.read(buffer);
                currentMessage += new String(buffer, 0, length);
                matcher = expectedMessage.matcher(currentMessage);
            } while (!matcher.find());
            LOG.trace(format("received telnet response:\n----------------\n%s\n----------------", matcher.group()));
            currentMessage = currentMessage.substring(matcher.end(0));
            return matcher;
        } catch (IOException exception) {
            LOG.warn("caught exception while reading input from telnet", exception);
            throw new VlcConnectionException(exception);
        }
    }
}
