package ch.wellernet.vlclib;

import static java.lang.String.format;

public class VlcProperty {
    private final String name;
    private final String value;

    public VlcProperty(String name, String value) {
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
        return format("%s=%s", name, value);
    }
}
