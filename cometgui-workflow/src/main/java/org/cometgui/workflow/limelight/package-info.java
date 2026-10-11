/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 3, as published
 * by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * The full licence is the LICENSE file at the root of this repository. If it
 * is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

/**
 * A finished run converted to Limelight XML: the converter installed on first use through the one
 * Tool Manager, every prerequisite checked by name before anything starts, and every conversion
 * recorded in the run's event log (phase 12, work unit 3; design decisions P12-1, P12-3, P12-5 to
 * P12-8, and the proposed P12-18).
 *
 * <ul>
 *   <li>{@link org.cometgui.workflow.limelight.LimelightProvisioning} reads which converter the
 *       Tool Manager has -- installed and verified, installable, being installed or unavailable
 *       ({@link org.cometgui.workflow.limelight.LimelightConverterStatus}) -- and starts an install
 *       through the {@link org.cometgui.domain.tools.ToolManager} port, the only installer there
 *       is.
 *   <li>{@link org.cometgui.workflow.limelight.LimelightConversions} is <strong>the one way a
 *       conversion is started</strong>. {@link
 *       org.cometgui.workflow.limelight.LimelightConversions#check} answers whether a run can be
 *       converted now and, if not, every reason ({@link
 *       org.cometgui.workflow.limelight.LimelightReadiness}, {@link
 *       org.cometgui.workflow.limelight.LimelightRefusal}, {@link
 *       org.cometgui.workflow.limelight.LimelightPrerequisite}); {@link
 *       org.cometgui.workflow.limelight.LimelightConversions#convert} asks the same question and
 *       then runs {@code org.cometgui.tools.limelight.LimelightConverter} through the process
 *       service, logging it to {@code logs/limelight-converter.log} and recording {@code
 *       limelight.converting} before it starts and {@code limelight.converted} or {@code
 *       limelight.unconverted} after it is judged.
 *   <li>A run's settings -- the Limelight q-value cutoff and the converter's options -- are kept in
 *       {@code results/limelight-settings.json} by {@code
 *       org.cometgui.workflow.storage.LimelightSettingsStore}, apart from the display filters;
 *       {@link org.cometgui.workflow.limelight.LimelightConversions#saveSettings} refuses a decoy
 *       import the run's own Percolator output cannot honour.
 *   <li>{@link org.cometgui.workflow.limelight.LimelightUploads} is <strong>the one way an upload
 *       is started</strong> (phase 12, work unit 5; {@code R-SEC-04}): {@link
 *       org.cometgui.workflow.limelight.LimelightUploads#destination} checks and returns the {@link
 *       org.cometgui.workflow.limelight.LimelightDestination} the interface shows -- the exact
 *       server and project, and a file one of the run's own conversions recorded, unchanged -- and
 *       {@link org.cometgui.workflow.limelight.LimelightUploads#upload} sends it only on the {@link
 *       org.cometgui.workflow.limelight.LimelightUploadConfirmation} that destination makes,
 *       logging to {@code logs/limelight-upload.log} and recording {@code limelight.uploading}
 *       before the first byte and {@code limelight.uploaded} or {@code limelight.unuploaded} after.
 *       The key is in neither.
 * </ul>
 *
 * <p>Written by phase 12, work units 3 and 5.
 */
package org.cometgui.workflow.limelight;
