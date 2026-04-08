package org.migration.util;

import org.apache.commons.codec.digest.DigestUtils;

public class Sha256HashCalculator implements HashCalculator {
    @Override
    public String calculate(String content) {
        return DigestUtils.sha256Hex(content);
    }
}
