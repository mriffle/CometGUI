#!/usr/bin/env python3
"""Compute the Maven modules a change must build, as a CLOSED set.

`-pl X -am -amd` is NOT closed: -amd adds X's dependents, but their OWN other
dependencies are not added, so Maven resolves those from the repository. This
build runs `verify`, never `install`, so those artefacts are absent or stale and
the reactor fails with NoClassDefFoundError -- which reads as a test failure in
code nobody touched. Proven on 2026-09-18: `-pl cometgui-params-percolator -am
-amd` pulled in cometgui-app without cometgui-tools.

Closure: S = changed + everything that depends on them (transitively),
then S += everything they depend on (transitively).
"""
import glob, re, sys, xml.etree.ElementTree as ET

NS = "{http://maven.apache.org/POM/4.0.0}"

def module_deps():
    deps = {}
    for pom in glob.glob("cometgui-*/pom.xml"):
        mod = pom.split("/")[0]
        root = ET.parse(pom).getroot()
        own = set()
        for d in root.iter(f"{NS}dependency"):
            a = d.find(f"{NS}artifactId")
            if a is not None and a.text and a.text.startswith("cometgui-"):
                own.add(a.text)
        deps[mod] = own
    return deps

def closure(seeds):
    deps = module_deps()
    dependents = {m: set() for m in deps}
    for m, ds in deps.items():
        for d in ds:
            dependents.setdefault(d, set()).add(m)

    s = set(seeds)
    changed = True
    while changed:                      # pull in dependents, transitively
        changed = False
        for m in list(s):
            for d in dependents.get(m, ()):
                if d not in s:
                    s.add(d); changed = True
    changed = True
    while changed:                      # then their dependencies, transitively
        changed = False
        for m in list(s):
            for d in deps.get(m, ()):
                if d not in s:
                    s.add(d); changed = True
    return sorted(s & set(deps))

if __name__ == "__main__":
    print(",".join(closure(sys.argv[1:])))
