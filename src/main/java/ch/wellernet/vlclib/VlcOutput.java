package ch.wellernet.vlclib;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.apache.commons.lang3.StringUtils.join;

import java.util.LinkedList;
import java.util.List;

public class VlcOutput {
    public static class Builder implements org.apache.commons.lang3.builder.Builder<VlcOutput> {

        private String currentModuleName;
        private List<VlcProperty> currentModuleProperties;

        private final List<VlcModule> modules;

        public Builder() {
            this.modules = new LinkedList<VlcModule>();
        }

        @Override
        public VlcOutput build() {
            if (currentModuleName != null) {
                modules.add(new VlcModule(currentModuleName, currentModuleProperties
                        .toArray(new VlcProperty[currentModuleProperties.size()])));
            }
            return new VlcOutput(modules);
        }

        public Builder module(String name) {
            currentModuleProperties = new LinkedList<VlcProperty>();
            currentModuleName = name;
            return this;
        }

        public Builder property(String name, String value) {
            currentModuleProperties.add(new VlcProperty(name, value));
            return this;
        }
    }

    private final List<VlcModule> modules;

    public VlcOutput(List<VlcModule> modules) {
        this.modules = unmodifiableList(modules);
    }

    public VlcOutput(VlcModule... modules) {
        this(asList(modules));
    }

    public List<VlcModule> getModules() {
        return modules;
    }

    @Override
    public String toString() {
        return format("#%s", join(modules, ':'));
    }
}
