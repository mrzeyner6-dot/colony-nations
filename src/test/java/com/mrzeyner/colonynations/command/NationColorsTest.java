package com.mrzeyner.colonynations.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NationColorsTest {
    @Test
    void acceptsHashOptionalAndIgnoresCase() {
        assertEquals(0x336699, NationColors.parse("#336699"));
        assertEquals(0x336699, NationColors.parse("336699"));
        assertEquals(0xABCDEF, NationColors.parse("#AbCdEf"));
        assertEquals(0x336699, NationColors.parse("  #336699  "));
    }

    @Test
    void rejectsMalformedColors() {
        assertNull(NationColors.parse("#12345"));
        assertNull(NationColors.parse("#GGGGGG"));
        assertNull(NationColors.parse("red"));
        assertNull(NationColors.parse(""));
        assertNull(NationColors.parse("   "));
        assertNull(NationColors.parse(null));
    }
}
