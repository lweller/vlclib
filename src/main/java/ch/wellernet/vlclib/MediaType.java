package ch.wellernet.vlclib;

public enum MediaType {

    BROADCAST("broadcast"), VOD("vod"), SCHEDULE("schedule");

    private String value;

    private MediaType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
