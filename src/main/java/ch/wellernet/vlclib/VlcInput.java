package ch.wellernet.vlclib;

import java.io.File;

public class VlcInput {
    private final String path;

    public VlcInput(String path) {
        this.path = path;
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
        VlcInput other = (VlcInput) obj;
        if (path == null) {
            if (other.path != null) {
                return false;
            }
        } else if (!path.equals(other.path)) {
            return false;
        }
        return true;
    }

    public File getFile() {
        return new File(path);
    }

    public String getPath() {
        return path;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (path == null ? 0 : path.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return path;
    }

}
