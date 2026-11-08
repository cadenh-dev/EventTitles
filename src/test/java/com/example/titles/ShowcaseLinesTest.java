package com.example.titles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.titles.api.ShowcaseLines;
import com.example.titles.api.ShowcaseSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShowcaseLinesTest {
    @Test
    void activeTitleComesFirst() {
        assertEquals(List.of("<red>Active</red>", "Pinned"),
            ShowcaseLines.compose("<red>Active</red>", List.of("Pinned"), 5));
    }

    @Test
    void deduplicatesByVisibleTextIgnoringCase() {
        assertEquals(List.of("<red>Boom</red>"),
            ShowcaseLines.compose("<red>Boom</red>", List.of("<gold>boom</gold>"), 5));
    }

    @Test
    void capsLines() {
        assertEquals(List.of("Active", "One"),
            ShowcaseLines.compose("Active", List.of("One", "Two"), 2));
    }

    @Test
    void skipsNullAndBlankTitles() {
        assertEquals(List.of("Pinned"),
            ShowcaseLines.compose("  ", java.util.Arrays.asList(null, "", " <gray> </gray> ", "Pinned"), 5));
        assertEquals(List.of("Pinned"), ShowcaseLines.compose(null, List.of("Pinned"), 5));
    }

    @Test
    void minimumLineLimitIsOne() {
        assertEquals(List.of("Active"), ShowcaseLines.compose("Active", List.of("Pinned"), 0));
    }

    @Test
    void settingsClampTitleCount() {
        assertEquals(1, new ShowcaseSettings(true, 0).maxTitles());
        assertEquals(5, new ShowcaseSettings(true, 9).maxTitles());
    }
}
