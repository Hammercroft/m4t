#!/bin/sh

# Exit immediately on error
set -e

# Check for javac
if ! command -v javac >/dev/null 2>&1; then
  echo "Error: javac not found. Please install a JDK."
  exit 1
fi

# Check for jar
if ! command -v jar >/dev/null 2>&1; then
  echo "Error: jar not found. Please install a JDK."
  exit 1
fi

# Clean previous build
rm -f M4TChatProgram.class M4TChatProgram.jar

# Compile
echo "Compiling M4TChatProgram.java..."
javac M4TChatProgram.java

# Package into JAR
echo "Creating M4TChatProgram.jar..."
jar cfe M4TChatProgram.jar M4TChatProgram M4TChatProgram.class

echo "Build complete: ./M4TChatProgram.jar"
echo "Run with: java -jar M4TChatProgram.jar"
