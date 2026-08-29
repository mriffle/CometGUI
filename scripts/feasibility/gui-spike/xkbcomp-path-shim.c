/*
 * CometGUI -- Phase 00, work unit 7: GUI automation spike.
 *
 * THROWAWAY FEASIBILITY SPIKE, NOT PRODUCT CODE.  Nothing in the product
 * uses LD_PRELOAD; this exists only so the *headed* half of the spike can be
 * attempted on a host where nothing may be installed.
 *
 * The problem: Xvfb compiles its keymap by running xkbcomp through
 * /bin/sh, at the absolute path baked in at build time -- Debian's is
 * "/usr/bin/xkbcomp".  This host has no X packages installed and /usr/bin is
 * not writable (and writing there would be a host install, which is
 * forbidden), so a project-local Xvfb dies with:
 *
 *     sh: 1: /usr/bin/xkbcomp: not found
 *     XKB: Failed to compile keymap
 *     Fatal server error: Failed to activate virtual core keyboard: 2
 *
 * User namespaces are not permitted here either ("unshare: Operation not
 * permitted"), so a bind mount is not available.
 *
 * The fix: the server runs the command with execl("/bin/sh", "sh", "-c", cmd).
 * Interposing execl and rewriting "/usr/bin/xkbcomp" inside cmd to the
 * project-local xkbcomp named by $XKBCOMP_PATH costs twenty lines and touches
 * nothing outside the Xvfb process tree.
 *
 * Build:  gcc -shared -fPIC -o xkbcomp-path-shim.so xkbcomp-path-shim.c
 * Use:    LD_PRELOAD=.../xkbcomp-path-shim.so XKBCOMP_PATH=.../xkbcomp Xvfb ...
 */
#define _GNU_SOURCE
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define NEEDLE "/usr/bin/xkbcomp"

/* Returns a rewritten copy of s, or NULL when nothing needed rewriting. */
static char *rewrite(const char *s)
{
    const char *repl = getenv("XKBCOMP_PATH");
    const char *hit;
    char *out;
    size_t pre;

    if (s == NULL || repl == NULL || *repl == '\0')
        return NULL;
    hit = strstr(s, NEEDLE);
    if (hit == NULL)
        return NULL;

    out = malloc(strlen(s) - strlen(NEEDLE) + strlen(repl) + 1);
    if (out == NULL)
        return NULL;
    pre = (size_t)(hit - s);
    memcpy(out, s, pre);
    strcpy(out + pre, repl);
    strcat(out, hit + strlen(NEEDLE));
    return out;
}

int execl(const char *path, const char *arg, ...)
{
    char *argv[64];
    va_list ap;
    int i = 0, j;

    argv[i++] = (char *)arg;
    va_start(ap, arg);
    while (i < 63) {
        char *a = va_arg(ap, char *);
        argv[i++] = a;
        if (a == NULL)
            break;
    }
    va_end(ap);
    argv[63] = NULL;

    for (j = 0; argv[j] != NULL; j++) {
        char *r = rewrite(argv[j]);
        if (r != NULL)
            argv[j] = r;
    }
    return execv(path, argv);
}
