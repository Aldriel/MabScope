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

package com.bioinfo.mabscope.desktop.controller;

import com.bioinfo.mabscope.desktop.model.DesktopModel;
import com.bioinfo.mabscope.desktop.model.UiSettings;
import com.bioinfo.mabscope.desktop.view.FilePicker;

import javafx.util.Callback;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Creates the controllers declared in the FXML views and gives them the model they work on.
 *
 * <p>The main view is loaded first, so the main controller exists when the tabs are created: the
 * "?" button of each tab opens the help through it.
 */
public final class ControllerFactory implements Callback<Class<?>, Object> {

    private final DesktopModel model;
    private final UiSettings settings;
    private final Consumer<String> openUrl;
    private final String stylesheet;
    private final FilePicker filePicker;
    private MainController main;

    /**
     * Creates a factory for one main window.
     *
     * @param model application state shared by all the controllers
     * @param settings language and text size
     * @param openUrl opens web pages, files and e-mail links with the default application
     * @param stylesheet application stylesheet, also applied to dialogs and the help window
     * @param filePicker asks for the files to read and write
     */
    public ControllerFactory(
            DesktopModel model,
            UiSettings settings,
            Consumer<String> openUrl,
            String stylesheet,
            FilePicker filePicker) {
        this.model = Objects.requireNonNull(model, "model");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.openUrl = Objects.requireNonNull(openUrl, "openUrl");
        this.stylesheet = Objects.requireNonNull(stylesheet, "stylesheet");
        this.filePicker = Objects.requireNonNull(filePicker, "filePicker");
    }

    @Override
    public Object call(Class<?> type) {
        if (type == MainController.class) {
            main = new MainController(model, settings, openUrl, stylesheet);
            return main;
        }
        Consumer<String> showHelp = section -> main.showHelp(section);
        if (type == AntibodyTabController.class) {
            return new AntibodyTabController(model, settings.i18n(), showHelp);
        }
        if (type == PeptideMapTabController.class) {
            return new PeptideMapTabController(model, settings.i18n(), showHelp);
        }
        if (type == QuantificationTabController.class) {
            return new QuantificationTabController(model, settings.i18n(), showHelp, filePicker);
        }
        if (type == ReportTabController.class) {
            return new ReportTabController(
                    model, settings.i18n(), showHelp, filePicker, openUrl, Locale.getDefault());
        }
        if (type == FooterController.class) {
            return new FooterController(settings.i18n(), openUrl);
        }
        throw new IllegalArgumentException("Unexpected controller " + type);
    }
}
