package com.example.titles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.titles.api.TitleText;
import org.junit.jupiter.api.Test;

class TitleTextTest {
    @Test
    void unlimitedPassesThrough() {
        String t = "<red>Some very long title</red>";
        assertEquals(t, TitleText.truncateVisible(t, -1));
        assertEquals(t, TitleText.truncateVisible(t, 0));
    }

    @Test
    void shortTitleUnchanged() {
        assertEquals("<red>Hi</red>", TitleText.truncateVisible("<red>Hi</red>", 5));
    }

    @Test
    void truncatesVisibleAndClosesTags() {
        String out = TitleText.truncateVisible("<red><bold>Creeper Hugger</bold></red>", 7);
        assertEquals("<red><bold>Creeper</bold></red>", out);
        assertEquals(7, TitleText.visibleLength(out));
    }

    @Test
    void handlesArgumentTagsAndSelfClosing() {
        String out = TitleText.truncateVisible("<gradient:#ff0000:#0000ff>Dragon<br>Slayer</gradient>", 8);
        assertEquals("<gradient:#ff0000:#0000ff>Dragon<br>Sl</gradient>", out);
        assertEquals("DragonSl", TitleText.stripTags(out).replace("\n", ""));
    }
}
