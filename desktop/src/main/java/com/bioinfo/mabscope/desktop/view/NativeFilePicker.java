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

package com.bioinfo.mabscope.desktop.view;

import com.bioinfo.mabscope.desktop.i18n.Messages;
import com.bioinfo.mabscope.desktop.prefs.UserPreferences;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * File choosers of the operating system. They open in the folder used last time, remembered in the
 * user preferences.
 */
public final class NativeFilePicker implements FilePicker {

    private final UserPreferences preferences;

    /**
     * Creates the picker.
     *
     * @param preferences where the last folders are remembered
     */
    public NativeFilePicker(UserPreferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public Optional<Path> chooseResults(Window owner, Messages m) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(m.get("chooser.results.title"));
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter(
                                m.get("chooser.results.filter"), "*.txt", "*.tsv", "*.csv"),
                        new FileChooser.ExtensionFilter(m.get("chooser.all"), "*.*"));
        preferences.lastDirectory().map(Path::toFile).ifPresent(chooser::setInitialDirectory);
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return Optional.empty();
        }
        Path path = file.toPath();
        if (path.getParent() != null) {
            preferences.setLastDirectory(path.getParent());
        }
        return Optional.of(path);
    }

    @Override
    public Optional<Path> chooseReport(Window owner, Messages m, String suggestedName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(m.get("chooser.report.title"));
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(m.get("chooser.report.filter"), "*.pdf"));
        preferences.saveDirectory().map(Path::toFile).ifPresent(chooser::setInitialDirectory);
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return Optional.empty();
        }
        Path path = file.toPath();
        if (path.getParent() != null) {
            preferences.setSaveDirectory(path.getParent());
        }
        return Optional.of(path);
    }
}
