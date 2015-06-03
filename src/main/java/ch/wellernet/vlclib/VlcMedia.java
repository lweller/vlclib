package ch.wellernet.vlclib;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import java.util.List;

public class VlcMedia {
    private final String name;
    private final MediaType type;
    private final boolean enabed;
    private final VlcOutput output;
    private final List<VlcOption> options;

    public VlcMedia(String name, MediaType type, boolean enabed, VlcOutput output, VlcOption... options) {
        this.name = name;
        this.type = type;
        this.enabed = enabed;
        this.output = output;
        this.options = unmodifiableList(asList(options));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        VlcMedia other = (VlcMedia) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    public String getName() {
        return name;
    }

    public List<VlcOption> getOptions() {
        return options;
    }

    public VlcOutput getOutput() {
        return output;
    }

    public MediaType getType() {
        return type;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (name == null ? 0 : name.hashCode());
        return result;
    }

    public boolean isEnabed() {
        return enabed;
    }

    @Override
    public String toString() {
        return "[name=" + name + ", type=" + type + ", enabed=" + enabed + ", output=" + output + "]";
    }

}
