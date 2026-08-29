/**
 * Argument-array process execution: independent stdout and stderr streaming, timestamped lines, bounded in-memory buffers with logs written to disk as they arrive, cancellation with descendant termination, and explicit working directory and environment. This is the only package permitted to construct a ProcessBuilder; an ArchUnit rule in cometgui-archtests enforces that (R-PROC-02).
 *
 * <p>Filled by phase 03 (process service).</p>
 */
package org.cometgui.tools.process;
