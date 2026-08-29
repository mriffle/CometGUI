/**
 * Architecture tests over every product module: the domain must not depend on JavaFX; tool adapters, provenance and hashing must not depend on the UI; the parameter parser and writer must not depend on JavaFX; no cycles between major layers; and ProcessBuilder construction is confined to the process service (R-PROC-02).
 *
 * <p>Filled by phase 01 unit 3, which adds ArchUnit and the rules themselves.</p>
 */
package org.cometgui.archtests;
