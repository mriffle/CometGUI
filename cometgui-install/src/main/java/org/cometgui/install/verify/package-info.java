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
 * Deciding whether a downloaded file is the artefact the manifest pinned.
 *
 * <p>{@code R-SEC-02} lives here: <em>SHA-256 verification is mandatory before an executable is
 * launched. MD5 shall also be computed and recorded for provenance but shall never be the security
 * trust mechanism.</em> {@link org.cometgui.install.verify.ArtefactVerifier} computes both digests
 * through the project's one {@link org.cometgui.domain.ports.HashService} and answers a {@link
 * org.cometgui.install.verify.VerificationResult} that distinguishes a match, a SHA-256 mismatch, a
 * size mismatch and an absent file. A file whose MD5 agrees and whose SHA-256 does not is a
 * rejection; a file whose SHA-256 agrees is accepted whatever its MD5 says.
 *
 * <p>{@link org.cometgui.install.verify.VerifiedDownloader} composes the fetch and the decision,
 * and carries the one rule the measured server forces: <strong>a resumed download that fails its
 * checksum is discarded and fetched again from zero, and is never resumed a second time.</strong>
 * GitHub's release host ignores {@code If-Range}, so a re-tagged asset splices bytes from two files
 * with no HTTP status revealing it; resuming again splices the same corruption back in and fails
 * identically, which reads as an upstream fault when it is the client's own. A restart that also
 * fails is a genuine disagreement between upstream and the manifest.
 *
 * <p>Every rejection deletes the file before raising {@link
 * org.cometgui.install.verify.ArtefactVerificationException}, so nothing unverified is left for a
 * later stage to execute. Holding a {@link org.cometgui.install.verify.VerifiedArtefact} is itself
 * the evidence that the SHA-256 matched.
 *
 * <p>This package is mutation-critical: {@code org.cometgui.install.verify.*} is in the root POM's
 * {@code targetClasses}, which is the build saying that a mutation able to disable checksum
 * verification must not survive.
 */
package org.cometgui.install.verify;
