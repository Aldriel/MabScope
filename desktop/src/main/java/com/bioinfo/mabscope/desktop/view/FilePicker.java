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

import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Asks the user for the files to read and write. The application uses the file choosers of the
 * operating system ({@link NativeFilePicker}); the functional tests answer without a window.
 */
public interface FilePicker {

    /**
     * Asks for a result file of an MS software.
     *
     * @param owner window owning the dialog
     * @param m messages in the current language
     * @return the file, or empty if the user cancels
     */
    Optional<Path> chooseResults(Window owner, Messages m);

    /**
     * Asks where to save the PDF report.
     *
     * @param owner window owning the dialog
     * @param m messages in the current language
     * @param suggestedName name proposed for the file
     * @return the file, or empty if the user cancels
     */
    Optional<Path> chooseReport(Window owner, Messages m, String suggestedName);
}
