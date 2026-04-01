package org.migration;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChecksumUtil {

    public static String getHash(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        return DigestUtils.sha256Hex(content);
    }
}
