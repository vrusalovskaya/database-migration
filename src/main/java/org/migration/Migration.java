package org.migration;

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
    private String filename;
    private String checkSum;
}
