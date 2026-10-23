#!/usr/bin/env python3
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# x11_windows.py -- read an X server's window tree from OUTSIDE the
# application, and ask a window to close the way a window manager's close
# button does (Phase 14, unit 5).
#
#   python3 x11_windows.py list   [--display :N]
#   python3 x11_windows.py wait   --pid PID --title TITLE --deadline SECONDS
#   python3 x11_windows.py close  --window ID
#
# The project's X11 closure (tools/x11-bookworm-20260829) carries Xvfb but no
# xwininfo, xprop or xdotool, and nothing may be installed on this host, so
# this talks to libX11 from that closure through ctypes.  The library is found
# through LD_LIBRARY_PATH (the closure's lib directory must be on it), and
# DISPLAY names the server unless --display is given.
#
# "list" prints one line per window anywhere in the tree that has a name:
#     <id-hex> <map-state> pid=<_NET_WM_PID or -> <name>
# "wait" polls (a bounded poll on an observable fact -- the window tree -- not
# a fixed sleep) until a VIEWABLE window titled TITLE whose _NET_WM_PID is PID
# exists, prints it and exits 0; exits 1 naming everything it saw otherwise,
# or as soon as PID has exited (a zombie counts as exited).
# "close" sends WM_DELETE_WINDOW (ICCCM), which a toolkit treats exactly as a
# click on the window's close button.

import argparse
import ctypes
import ctypes.util
import os
import sys
import time

IS_VIEWABLE = 2
MAP_STATES = {0: "unmapped", 1: "unviewable", 2: "viewable"}


class XWindowAttributes(ctypes.Structure):
    _fields_ = [
        ("x", ctypes.c_int), ("y", ctypes.c_int),
        ("width", ctypes.c_int), ("height", ctypes.c_int),
        ("border_width", ctypes.c_int), ("depth", ctypes.c_int),
        ("visual", ctypes.c_void_p), ("root", ctypes.c_ulong),
        ("class", ctypes.c_int), ("bit_gravity", ctypes.c_int),
        ("win_gravity", ctypes.c_int), ("backing_store", ctypes.c_int),
        ("backing_planes", ctypes.c_ulong), ("backing_pixel", ctypes.c_ulong),
        ("save_under", ctypes.c_int), ("colormap", ctypes.c_ulong),
        ("map_installed", ctypes.c_int), ("map_state", ctypes.c_int),
        ("all_event_masks", ctypes.c_long), ("your_event_mask", ctypes.c_long),
        ("do_not_propagate_mask", ctypes.c_long),
        ("override_redirect", ctypes.c_int), ("screen", ctypes.c_void_p),
    ]


class XClientMessageEvent(ctypes.Structure):
    _fields_ = [
        ("type", ctypes.c_int), ("serial", ctypes.c_ulong),
        ("send_event", ctypes.c_int), ("display", ctypes.c_void_p),
        ("window", ctypes.c_ulong), ("message_type", ctypes.c_ulong),
        ("format", ctypes.c_int), ("data", ctypes.c_long * 5),
    ]


class XEvent(ctypes.Union):
    _fields_ = [("xclient", XClientMessageEvent), ("pad", ctypes.c_long * 24)]


def load_x11():
    for name in ("libX11.so.6", ctypes.util.find_library("X11")):
        if not name:
            continue
        try:
            lib = ctypes.CDLL(name)
            break
        except OSError:
            continue
    else:
        sys.exit("x11_windows.py: libX11 not loadable -- put the project X11 closure's "
                 "usr/lib/x86_64-linux-gnu on LD_LIBRARY_PATH")
    lib.XOpenDisplay.restype = ctypes.c_void_p
    lib.XOpenDisplay.argtypes = [ctypes.c_char_p]
    lib.XDefaultRootWindow.restype = ctypes.c_ulong
    lib.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
    lib.XQueryTree.argtypes = [
        ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(ctypes.c_ulong),
        ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.POINTER(ctypes.c_ulong)),
        ctypes.POINTER(ctypes.c_uint)]
    lib.XFetchName.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                               ctypes.POINTER(ctypes.c_char_p)]
    lib.XInternAtom.restype = ctypes.c_ulong
    lib.XInternAtom.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]
    lib.XGetWindowProperty.argtypes = [
        ctypes.c_void_p, ctypes.c_ulong, ctypes.c_ulong, ctypes.c_long, ctypes.c_long,
        ctypes.c_int, ctypes.c_ulong, ctypes.POINTER(ctypes.c_ulong),
        ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_ulong),
        ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.c_void_p)]
    lib.XGetWindowAttributes.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                                         ctypes.POINTER(XWindowAttributes)]
    lib.XFree.argtypes = [ctypes.c_void_p]
    lib.XSendEvent.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int,
                               ctypes.c_long, ctypes.POINTER(XEvent)]
    lib.XFlush.argtypes = [ctypes.c_void_p]
    lib.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
    lib.XCloseDisplay.argtypes = [ctypes.c_void_p]
    # A window can vanish between XQueryTree and the next request; the default
    # handler would exit the process for that BadWindow, so ignore errors.
    handler_type = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_void_p, ctypes.c_void_p)
    lib._ignore = handler_type(lambda d, e: 0)
    lib.XSetErrorHandler.argtypes = [handler_type]
    lib.XSetErrorHandler(lib._ignore)
    return lib


def open_display(lib, name):
    target = name or os.environ.get("DISPLAY")
    if not target:
        sys.exit("x11_windows.py: no DISPLAY and no --display")
    dpy = lib.XOpenDisplay(target.encode())
    if not dpy:
        sys.exit(f"x11_windows.py: cannot open display {target}")
    return dpy


def property_bytes(lib, dpy, window, atom_name, any_type=True):
    atom = lib.XInternAtom(dpy, atom_name.encode(), 1)
    if not atom:
        return None, 0
    actual_type = ctypes.c_ulong()
    actual_format = ctypes.c_int()
    nitems = ctypes.c_ulong()
    after = ctypes.c_ulong()
    data = ctypes.c_void_p()
    status = lib.XGetWindowProperty(dpy, window, atom, 0, 1024, 0, 0,
                                    ctypes.byref(actual_type), ctypes.byref(actual_format),
                                    ctypes.byref(nitems), ctypes.byref(after),
                                    ctypes.byref(data))
    if status != 0 or not data.value or nitems.value == 0:
        if data.value:
            lib.XFree(data)
        return None, 0
    fmt = actual_format.value
    if fmt == 32:
        values = ctypes.cast(data, ctypes.POINTER(ctypes.c_long))
        result = [values[i] for i in range(nitems.value)]
    else:
        result = ctypes.string_at(data, nitems.value)
    lib.XFree(data)
    return result, fmt


def window_name(lib, dpy, window):
    utf8, fmt = property_bytes(lib, dpy, window, "_NET_WM_NAME")
    if utf8 is not None and fmt == 8:
        return utf8.decode("utf-8", "replace")
    name = ctypes.c_char_p()
    if lib.XFetchName(dpy, window, ctypes.byref(name)) and name.value is not None:
        text = name.value.decode("latin-1", "replace")
        return text
    return None


def windows(lib, dpy):
    found = []
    stack = [lib.XDefaultRootWindow(dpy)]
    while stack:
        window = stack.pop()
        root = ctypes.c_ulong()
        parent = ctypes.c_ulong()
        children = ctypes.POINTER(ctypes.c_ulong)()
        count = ctypes.c_uint()
        if lib.XQueryTree(dpy, window, ctypes.byref(root), ctypes.byref(parent),
                          ctypes.byref(children), ctypes.byref(count)):
            for i in range(count.value):
                stack.append(children[i])
            if children:
                lib.XFree(children)
        name = window_name(lib, dpy, window)
        if name is None:
            continue
        attributes = XWindowAttributes()
        state = -1
        if lib.XGetWindowAttributes(dpy, window, ctypes.byref(attributes)):
            state = attributes.map_state
        pid_values, _ = property_bytes(lib, dpy, window, "_NET_WM_PID")
        pid = pid_values[0] if pid_values else None
        found.append((window, state, pid, name,
                      attributes.width if state >= 0 else 0,
                      attributes.height if state >= 0 else 0))
    return found


def alive(pid):
    """True while PID runs; a zombie (exited, not yet reaped) is not alive."""
    try:
        with open(f"/proc/{pid}/stat", encoding="ascii", errors="replace") as stat:
            return stat.read().rsplit(")", 1)[1].split()[0] != "Z"
    except OSError:
        return False


def describe(entry):
    window, state, pid, name, width, height = entry
    return (f"0x{window:x} {MAP_STATES.get(state, 'gone')} {width}x{height} "
            f"pid={pid if pid is not None else '-'} {name!r}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["list", "wait", "close"])
    parser.add_argument("--display")
    parser.add_argument("--pid", type=int)
    parser.add_argument("--title")
    parser.add_argument("--deadline", type=float, default=60.0)
    parser.add_argument("--window")
    args = parser.parse_args()
    lib = load_x11()
    dpy = open_display(lib, args.display)

    if args.action == "list":
        for entry in windows(lib, dpy):
            print(describe(entry))
        return 0

    if args.action == "close":
        if not args.window:
            sys.exit("x11_windows.py close: --window is required")
        window = int(args.window, 0)
        event = XEvent()
        event.xclient.type = 33  # ClientMessage
        event.xclient.window = window
        event.xclient.message_type = lib.XInternAtom(dpy, b"WM_PROTOCOLS", 0)
        event.xclient.format = 32
        event.xclient.data[0] = lib.XInternAtom(dpy, b"WM_DELETE_WINDOW", 0)
        event.xclient.data[1] = 0  # CurrentTime
        if not lib.XSendEvent(dpy, window, 0, 0, ctypes.byref(event)):
            print(f"x11_windows.py: XSendEvent to 0x{window:x} failed", file=sys.stderr)
            return 1
        lib.XSync(dpy, 0)
        print(f"sent WM_DELETE_WINDOW to 0x{window:x}")
        return 0

    if args.pid is None or args.title is None:
        sys.exit("x11_windows.py wait: --pid and --title are required")
    deadline = time.monotonic() + args.deadline
    seen = []
    while True:
        seen = windows(lib, dpy)
        for entry in seen:
            window, state, pid, name, _, _ = entry
            if state == IS_VIEWABLE and pid == args.pid and name == args.title:
                print(describe(entry))
                return 0
        if not alive(args.pid):
            print(f"x11_windows.py: process {args.pid} exited before a viewable window "
                  f"titled {args.title!r} appeared; the display held: "
                  + ("; ".join(describe(e) for e in seen) or "no named window"),
                  file=sys.stderr)
            return 1
        if time.monotonic() >= deadline:
            print(f"x11_windows.py: no viewable window titled {args.title!r} owned by pid "
                  f"{args.pid} within {args.deadline:g} s; the display held: "
                  + ("; ".join(describe(e) for e in seen) or "no named window"),
                  file=sys.stderr)
            return 1
        # The poll interval of a bounded wait on the window tree.
        time.sleep(0.25)


if __name__ == "__main__":
    sys.exit(main())
