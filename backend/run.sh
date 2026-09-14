#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# kotlin-maven-plugin 1.9.x падает с "IllegalArgumentException: <версия>" на очень новых JDK
# (см. https://youtrack.jetbrains.com/issue/KT-83610) - его встроенный парсер версии Java
# не понимает JDK 25/26. Дело не в коде проекта: просто запускаем сборку под JDK 21.

find_brew_prefix() {
  if command -v brew >/dev/null 2>&1; then
    brew --prefix openjdk@21 2>/dev/null || true
  fi
}

CANDIDATES=(
  "$(find_brew_prefix)/libexec/openjdk.jdk/Contents/Home"
  "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
)

for candidate in "${CANDIDATES[@]}"; do
  if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
    export JAVA_HOME="$candidate"
    break
  fi
done

if [ -z "${JAVA_HOME:-}" ]; then
  echo "JDK 21 от Homebrew не найден (ожидался .../openjdk@21/libexec/openjdk.jdk/Contents/Home)." >&2
  echo "Установите: brew install openjdk@21" >&2
  echo "Либо посмотрите все установленные JDK командой: /usr/libexec/java_home -V" >&2
  echo "и запустите: JAVA_HOME=<путь_к_JDK21> mvn spring-boot:run" >&2
  exit 1
fi

echo "Используем JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version
exec mvn spring-boot:run
