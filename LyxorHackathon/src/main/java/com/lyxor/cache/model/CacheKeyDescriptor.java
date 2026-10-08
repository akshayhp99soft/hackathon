package com.lyxor.cache.model;

import java.util.Objects;

public class CacheKeyDescriptor {
    private final String namespace;
    private final String key;
    private String versionTag;

    public CacheKeyDescriptor(String namespace, String key, String versionTag) {
        this.namespace = Objects.requireNonNull(namespace);
        this.key = Objects.requireNonNull(key);
        this.versionTag = versionTag != null ? versionTag : "v1";
    }

    public void updateVersionTag(String newVersionTag) {
        this.versionTag = newVersionTag;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKey() {
        return key;
    }

    public String getVersionTag() {
        return versionTag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKeyDescriptor that = (CacheKeyDescriptor) o;
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(key, that.key) &&
                Objects.equals(versionTag, that.versionTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key, versionTag);
    }
}
