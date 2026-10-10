/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bioinfo.mabscope.desktop.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.desktop.DesktopApp;
import com.bioinfo.mabscope.desktop.FxThread;
import com.bioinfo.mabscope.desktop.model.UiSettings;
import com.bioinfo.mabscope.desktop.prefs.UserPreferences;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Functional tests of the help window and of the rendering of the guide, on headless JavaFX. */
class HelpWindowTest {

    private final Preferences node =
            Preferences.userRoot().node("mabscope-help-test-" + UUID.randomUUID());
    private final List<String> openedUrls = new ArrayList<>();
    private UiSettings settings;
    private Stage owner;
    private HelpWindow help;

    @BeforeAll
    static void startJavaFx() {
        FxThread.start();
    }

    @BeforeEach
    void createWindow() {
        FxThread.run(
                () -> {
                    settings = new UiSettings(new UserPreferences(node));
                    settings.setLocale(Locale.ENGLISH);
                    owner = new Stage();
                    owner.setScene(new Scene(new VBox(), 400, 300));
                    owner.show();
                    help = new HelpWindow(owner, settings, DesktopApp.STYLESHEET, openedUrls::add);
                });
    }

    @AfterEach
    void closeWindows() throws BackingStoreException {
        FxThread.run(
                () -> {
                    help.stage().hide();
                    owner.hide();
                });
        node.removeNode();
    }

    private Scene scene() {
        return help.stage().getScene();
    }

    private VBox content() {
        return (VBox) ((ScrollPane) scene().lookup(".help-content")).getContent();
    }

    private TextField searchField() {
        return (TextField) scene().lookup("#helpSearchField");
    }

    @SuppressWarnings("unchecked")
    private ListView<HelpDocument.SearchHit> sections() {
        return (ListView<HelpDocument.SearchHit>) scene().lookup("#helpSectionList");
    }

    private void accelerator(KeyCodeCombination keys) {
        FxThread.run(() -> scene().getAccelerators().get(keys).run());
    }

    @Test
    void everySectionOfBothGuidesIsRendered() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH)) {
            FxThread.run(() -> settings.setLocale(locale));
            for (HelpSection section : HelpDocument.load(locale).sections()) {
                FxThread.run(() -> help.show(section.id()));
                assertEquals(
                        section.id(),
                        FxThread.call(
                                () ->
                                        sections()
                                                .getSelectionModel()
                                                .getSelectedItem()
                                                .section()
                                                .id()));
                assertTrue(FxThread.call(() -> content().getChildren().size()) > 1, section.id());
            }
        }
        assertTrue(FxThread.call(() -> help.stage().getTitle()).contains("MabScope"));
    }

    @Test
    void imagesAndLinksOfTheGuide() {
        FxThread.run(() -> help.show("antibody"));
        assertFalse(FxThread.call(() -> content().lookupAll(".image-view")).isEmpty());
        FxThread.run(
                () -> content().lookupAll(".hyperlink").forEach(link -> ((Hyperlink) link).fire()));
        assertEquals(List.of("http://www.bioinf.org.uk/abs/info.html#cdrid"), openedUrls);
        FxThread.run(() -> help.show("unknown"));
        assertEquals(
                "start",
                FxThread.call(
                        () -> sections().getSelectionModel().getSelectedItem().section().id()),
                "unknown sections open the first one");
    }

    @Test
    void searchFiltersHighlightsAndClears() {
        FxThread.run(() -> help.show("start"));
        FxThread.run(() -> help.search("MaxQuant"));
        List<String> ids =
                FxThread.call(() -> sections().getItems().stream().map(h -> h.section().id()))
                        .toList();
        assertTrue(ids.contains("formats") && !ids.contains("report"), ids.toString());
        FxThread.run(() -> {});
        assertFalse(FxThread.call(() -> content().lookupAll(".help-highlight")).isEmpty());
        FxThread.run(
                () -> {
                    sections().applyCss();
                    sections().layout();
                });
        assertTrue(
                FxThread.call(
                        () ->
                                sections().lookupAll(".list-cell").stream()
                                        .map(c -> ((Labeled) c).getText())
                                        .anyMatch(t -> t != null && t.endsWith(")"))));

        FxThread.run(() -> help.search("IYPTNGYTR"));
        assertFalse(
                FxThread.call(() -> content().lookupAll(".help-code.help-highlight")).isEmpty(),
                "the example of the code block is highlighted");

        FxThread.run(() -> help.search("nonexistentword"));
        assertTrue(FxThread.call(() -> sections().getItems().isEmpty()));
        assertTrue(
                FxThread.call(() -> ((Label) content().getChildren().getFirst()).getText())
                        .contains("nonexistentword"));

        FxThread.run(
                () ->
                        scene().getRoot().lookupAll(".button").stream()
                                .map(Button.class::cast)
                                .filter(b -> b.getText().equals("Clear"))
                                .findFirst()
                                .orElseThrow()
                                .fire());
        assertEquals("", FxThread.call(() -> searchField().getText()));
    }

    @Test
    void keyboardShortcuts() {
        FxThread.run(() -> help.search("lot"));
        accelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
        accelerator(new KeyCodeCombination(KeyCode.ESCAPE));
        assertEquals("", FxThread.call(() -> searchField().getText()), "Escape clears the search");
        assertTrue(FxThread.call(() -> help.stage().isShowing()));
        accelerator(new KeyCodeCombination(KeyCode.ESCAPE));
        assertFalse(FxThread.call(() -> help.stage().isShowing()), "then closes the window");
    }

    @Test
    void languageChangeReloadsTheGuideOnTheSameSection() {
        FxThread.run(() -> help.show("quantification"));
        FxThread.run(() -> settings.setLocale(Locale.FRENCH));
        HelpDocument.SearchHit selected =
                FxThread.call(() -> sections().getSelectionModel().getSelectedItem());
        assertEquals("quantification", selected.section().id());
        assertEquals("Onglet Quantification LC-MS", selected.section().title());
    }

    @Test
    void rendererHandlesEveryBlock() {
        HelpSection section =
                HelpDocument.parse(
                                """
# Blocks
### Small heading
1. first
2. second
![Missing picture](images/none.png)
| Short | A long column whose cells wrap because they exceed forty characters |
|---|---|
| a | b |
| c |
```
code line
```
""")
                        .sections()
                        .getFirst();
        VBox box =
                FxThread.call(
                        () ->
                                new HelpRenderer(List.of(), new SimpleDoubleProperty(600), u -> {})
                                        .render(section));
        assertEquals(6, box.getChildren().size(), "title and five blocks");
        assertNotNull(box.lookup(".help-h3"));
        assertTrue(box.lookupAll(".image-view").isEmpty(), "missing images show their text");
        assertNotNull(box.lookup(".help-table-cell-alt"));
        Node code = box.lookup(".help-code");
        assertFalse(code.getStyleClass().contains(HelpRenderer.HIGHLIGHT));
        assertTrue(box.lookupAll(".help-list-marker").size() == 2);
        assertTrue(box.lookupAll(".image-view").stream().noneMatch(ImageView.class::isInstance));
    }
}
