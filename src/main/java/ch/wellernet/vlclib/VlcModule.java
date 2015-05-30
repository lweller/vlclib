package ch.wellernet.vlclib;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.apache.commons.lang3.StringUtils.join;

import java.util.List;

public class VlcModule {
    private final String name;
    private final List<VlcProperty> properties;

    public VlcModule(String name, List<VlcProperty> properties) {
        this.name = name;
        this.properties = unmodifiableList(properties);
    }

    public VlcModule(String name, VlcProperty... properties) {
        this(name, asList(properties));
    }

    public String getName() {
        return name;
    }

    public List<VlcProperty> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(name);
        if (!properties.isEmpty()) {
            builder.append(format("{%s}", join(properties.toArray(), ',')));
        }
        return builder.toString();
    }
}
