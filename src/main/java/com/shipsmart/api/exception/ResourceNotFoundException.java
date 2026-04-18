package com.shipsmart.api.exception;

public class ResourceNotFoundException extends RuntimeException {
    private final String resource;
    private final String identifier;

    public ResourceNotFoundException(String resource, String identifier) {
        super(resource + " not found: " + identifier);
        this.resource = resource;
        this.identifier = identifier;
    }

    public String getResource() { return resource; }
    public String getIdentifier() { return identifier; }
}
