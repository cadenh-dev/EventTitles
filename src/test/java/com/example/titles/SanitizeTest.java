package com.example.titles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.titles.api.TitleText;
import org.junit.jupiter.api.Test;

class SanitizeTest {
    @Test
    void keepsStyleTags() {
        String s = "<gradient:#ff0000:#0000ff><bold>Hi</bold></gradient> <#ff8800>x</#ff8800> <dark_grey>y <!italic>z";
        assertEquals(s, TitleText.sanitizeLlm(s, false));
    }

    @Test
    void dropsInteractiveAndDataTags() {
        assertEquals("click me", TitleText.sanitizeLlm("<click:run_command:/op me>click me</click>", false));
        assertEquals("a  b", TitleText.sanitizeLlm("a <selector:@a> b", false));
        assertEquals("t", TitleText.sanitizeLlm("<insert:x><hover:show_text:'y'>t</hover></insert>", false));
        assertEquals("<red>k</red>", TitleText.sanitizeLlm("<red><key:key.jump>k</red>", false));
    }

    @Test
    void playerTokenOnlyWhenAllowed() {
        assertEquals("<player> died", TitleText.sanitizeLlm("<player> died", true));
        assertEquals(" died", TitleText.sanitizeLlm("<player> died", false));
        assertEquals(" died", TitleText.sanitizeLlm("<player:x> died", true));
    }

    @Test
    void preservesEscapedBrackets() {
        assertEquals("\\<click> literal", TitleText.sanitizeLlm("\\<click> literal", false));
    }
}
