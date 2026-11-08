package com.example.titles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.titles.api.ChatPrefixSettings;
import com.example.titles.api.ChatPrefixSettings.Mode;
import com.example.titles.api.TitlesSettings;
import org.junit.jupiter.api.Test;

class ChatPrefixSettingsTest {
    @Test
    void replaceNeedsAllThreeTokens() {
        assertTrue(ChatPrefixSettings.isValidFormat(Mode.REPLACE, "<title> <player>: <message>"));
        assertFalse(ChatPrefixSettings.isValidFormat(Mode.REPLACE, "<title> <player>"));
        assertFalse(ChatPrefixSettings.isValidFormat(Mode.REPLACE, "<player>: <message>"));
    }

    @Test
    void prefixNeedsTitleOnly() {
        assertTrue(ChatPrefixSettings.isValidFormat(Mode.PREFIX, "[<title>] "));
        assertFalse(ChatPrefixSettings.isValidFormat(Mode.PREFIX, "<title> <player>: <message>"));
        assertFalse(ChatPrefixSettings.isValidFormat(Mode.PREFIX, null));
    }

    @Test
    void invalidFormatFallsBackToModeDefault() {
        assertEquals(ChatPrefixSettings.DEFAULT_REPLACE_FORMAT,
            new ChatPrefixSettings(true, Mode.REPLACE, "<title>", 24).format());
        assertEquals(ChatPrefixSettings.DEFAULT_PREFIX_FORMAT,
            new ChatPrefixSettings(true, Mode.PREFIX, ChatPrefixSettings.DEFAULT_REPLACE_FORMAT, 24).format());
        assertEquals(Mode.REPLACE, new ChatPrefixSettings(true, null, null, 24).mode());
    }

    @Test
    void modeParsing() {
        assertEquals(Mode.PREFIX, Mode.fromConfig(" Prefix ").orElseThrow());
        assertTrue(Mode.fromConfig("sideways").isEmpty());
    }

    @Test
    void interpolationDefaultsToThreeAndIsClamped() {
        assertEquals(3, TitlesSettings.defaults().displayInterpolationTicks());
        TitlesSettings d = TitlesSettings.defaults();
        TitlesSettings wild = new TitlesSettings(d.llmEnabled(), d.llmProvider(), d.llmEndpoint(), d.llmModel(),
            d.llmApiKey(), d.llmTimeoutSeconds(), d.llmTemperature(), d.maxTitleLength(), d.streakWindowMinutes(),
            d.defaultTitle(), d.displayOffsetY(), d.showToSelf(), d.announceToSelf(), d.chat(), d.tabList(),
            d.chatPrefix(), 99);
        assertEquals(10, wild.displayInterpolationTicks());
    }
}
