/**
 * Extractors selected by declared artefact kind, never by URL suffix (R-TOOL-01): ZIP, TAR_GZ, JAR, BARE_EXECUTABLE, DEB_PAYLOAD and PKG_PAYLOAD, each rejecting path traversal, absolute entries, unsafe symlinks and decompression bombs.
 *
 * <p>Filled by phase 05 (tool registry and installer).</p>
 */
package org.cometgui.install.archive;
