package org.migration.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.file.Path;

@AllArgsConstructor
@Getter
public class Resource {
    private String name;
    private String path;
}
