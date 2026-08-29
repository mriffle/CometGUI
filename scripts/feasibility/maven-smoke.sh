#!/usr/bin/env bash
#
# CometGUI -- Phase 00, work unit 5: build-tool smoke test.
#
#   bash scripts/feasibility/maven-smoke.sh
#
# Proves that the project-local Apache Maven actually builds and resolves
# against Maven Central on the pinned JDK -- "mvn -v prints a version" is not
# evidence that the build tool works. The throwaway spike from
# scripts/feasibility/jpackage-spike/ is compiled through Maven into an
# executable jar, that jar is run, and its output is checked.
#
# Everything stays inside /workspace: the local repository is forced to
# _build/m2repo so nothing is written to ~/.m2 on the host.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
BUILD="${ROOT}/_build/maven-smoke"
M2REPO="${ROOT}/_build/m2repo"

[ -f "${ROOT}/tools/env.sh" ] || {
    echo "FATAL: ${ROOT}/tools/env.sh missing. Run scripts/feasibility/install-toolchain.sh first." >&2
    exit 1
}
# shellcheck disable=SC1091
. "${ROOT}/tools/env.sh"

rm -rf -- "${BUILD}"
mkdir -p -- "${BUILD}/src/main/java" "${M2REPO}"
cp -- "${SCRIPT_DIR}/jpackage-spike/ToolchainProbe.java" "${BUILD}/src/main/java/"

cat > "${BUILD}/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<!-- Throwaway Phase 00 feasibility POM. Not the product build. -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.cometgui.spike</groupId>
  <artifactId>toolchain-probe</artifactId>
  <version>0.0.1</version>
  <packaging>jar</packaging>
  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.release>25</maven.compiler.release>
  </properties>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <version>3.4.2</version>
        <configuration>
          <archive><manifest><mainClass>ToolchainProbe</mainClass></manifest></archive>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
POM

echo "== mvn -v =="
mvn -v

echo
echo "== mvn package (local repository forced to ${M2REPO}) =="
mvn -B -f "${BUILD}/pom.xml" -Dmaven.repo.local="${M2REPO}" package

JAR="${BUILD}/target/toolchain-probe-0.0.1.jar"
[ -f "${JAR}" ] || { echo "FATAL: ${JAR} was not produced" >&2; exit 1; }
echo
echo "== produced artefact =="
ls -l "${JAR}"

echo
echo "== run the Maven-built executable jar =="
out="$(java -Dprobe.requireBundle=false -jar "${JAR}")"
printf '%s\n' "${out}"
printf '%s\n' "${out}" | grep -q '^PROBE RESULT        = PASS$' \
    || { echo "FATAL: the Maven-built jar did not report PASS" >&2; exit 1; }

echo
echo "OK: Apache Maven $(mvn -v | awk '/^Apache Maven /{print $3}') built and ran the spike on JDK $(java -version 2>&1 | head -1)"
