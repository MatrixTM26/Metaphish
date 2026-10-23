#!/usr/bin/env bash
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$PROJECT_ROOT/src/main/java"
RESOURCES="$PROJECT_ROOT/src/main/resources"
BUILD="$PROJECT_ROOT/target/classes"
JAR="$PROJECT_ROOT/target/metaphish.jar"
MAIN_CLASS="com.metaphish.Metaphish"

mkdir -p "$BUILD" "$PROJECT_ROOT/target"

echo "[*] Compiling sources..."

if command -v javac &>/dev/null; then
    find "$SRC" -name "*.java" | xargs javac -source 17 -target 17 -proc:none -d "$BUILD"
elif command -v ecj &>/dev/null; then
    find "$SRC" -name "*.java" | xargs ecj -proc:none -source 17 -target 17 -d "$BUILD"
else
    echo "[!] No Java compiler found. Install JDK (javac) or ECJ."
    exit 1
fi

echo "[*] Copying resources..."
cp -r "$RESOURCES/." "$BUILD/"

echo "[*] Packaging JAR..."
BUILD_DIR="$BUILD" JAR_PATH="$JAR" python3 - <<'PYEOF'
import zipfile, os, sys

BuildDir  = os.environ.get("BUILD_DIR")  or os.path.join(os.path.dirname(os.path.abspath(__file__)), "target/classes")
JarPath   = os.environ.get("JAR_PATH")   or os.path.join(os.path.dirname(os.path.abspath(__file__)), "target/metaphish.jar")
MainClass = "com.metaphish.Metaphish"

ManifestContent = (
    "Manifest-Version: 1.0\n"
    "Main-Class: " + MainClass + "\n"
    "\n"
)

with zipfile.ZipFile(JarPath, "w", zipfile.ZIP_DEFLATED) as Jar:
    Jar.writestr("META-INF/MANIFEST.MF", ManifestContent)
    for Root, Dirs, Files in os.walk(BuildDir):
        for File in Files:
            FilePath = os.path.join(Root, File)
            ArchivePath = os.path.relpath(FilePath, BuildDir)
            Jar.write(FilePath, ArchivePath)

print("[+] JAR created: " + JarPath)
PYEOF

echo "[+] Build complete: $JAR"
echo ""
echo "    Run: java -jar $JAR --help"
