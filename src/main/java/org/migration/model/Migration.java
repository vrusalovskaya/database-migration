package org.migration.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Migration {
    private String version;
    private String description;
    private Resource resource;
    private String checkSum;
}
