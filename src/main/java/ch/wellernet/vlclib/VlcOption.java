package ch.wellernet.vlclib;

import static java.lang.String.format;

public class VlcOption {
    private final String name;
    private final String value;

    public VlcOption(String name) {
        this(name, null);
    }

    public VlcOption(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value == null ? name : format("%s=%s", name, value);
    }
}
