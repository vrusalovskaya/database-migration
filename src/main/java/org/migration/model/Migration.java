package org.migration.model;

public record Migration(String version, String description, Resource resource, String checkSum) {
}
