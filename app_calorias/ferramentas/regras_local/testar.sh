#!/bin/bash
# Compila as regras do app (Nutricao.kt, Internet.kt e as funções de texto do
# Componentes.kt) e roda o NutricaoTeste sem o Android SDK, com o compilador
# Kotlin que vem dentro do Gradle. Serve para a sessão na nuvem, que não baixa o
# SDK nem o Maven Central; as telas (Compose) só compilam no workflow APK.
#
# Uso: ferramentas/regras_local/testar.sh   (de qualquer pasta)
# O org.json e o android.util.Base64 daqui são imitações mínimas, só para esses
# testes; o app de verdade usa os do Android.
set -euo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
RAIZ=$(cd "$AQUI/../.." && pwd)
APP="$RAIZ/app/src"
LIB=$(ls -d /opt/gradle*/lib 2>/dev/null | sort | tail -1)
COMPILADOR=$(ls "$LIB"/kotlin-compiler-embeddable-*.jar 2>/dev/null | head -1)
if [ -z "$LIB" ] || [ -z "$COMPILADOR" ]; then
  echo "Não achei o Gradle com o compilador Kotlin em /opt." >&2
  exit 1
fi
jar() { ls "$LIB"/"$1"-*.jar | head -1; }
SAIDA=$(mktemp -d)
trap 'rm -rf "$SAIDA"' EXIT
mkdir -p "$SAIDA/imitacoes" "$SAIDA/main" "$SAIDA/test" "$SAIDA/fonte"

javac -nowarn --release 17 -d "$SAIDA/imitacoes" $(find "$AQUI" -name '*.java')

# Só as funções puras do Componentes.kt (do lerNumero ao hoje), copiadas do arquivo real.
{
  echo "package com.rafaelnm90.calorias"
  echo "import java.time.LocalDate"
  sed -n '/^fun lerNumero/,/^fun hoje/p' "$APP/main/java/com/rafaelnm90/calorias/Componentes.kt"
} > "$SAIDA/fonte/ComponentesPuros.kt"

STDLIB=$(jar kotlin-stdlib)
CORROTINAS=$(jar kotlinx-coroutines-core-jvm)
CP_COMPILADOR="$COMPILADOR:$STDLIB:$(jar kotlin-script-runtime):$(jar kotlin-reflect):$(jar kotlin-daemon-embeddable):$(jar trove4j):$(jar annotations):$CORROTINAS"
CP_APP="$STDLIB:$CORROTINAS:$SAIDA/imitacoes"
JUNIT="$(jar junit):$(jar hamcrest-core)"

kotlinc() {
  java -Xmx2g -cp "$CP_COMPILADOR" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 "$@"
}

echo "== compilando as regras"
kotlinc -cp "$CP_APP" -d "$SAIDA/main" \
  "$APP/main/java/com/rafaelnm90/calorias/Nutricao.kt" \
  "$APP/main/java/com/rafaelnm90/calorias/Internet.kt" \
  "$SAIDA/fonte/ComponentesPuros.kt"
echo "== compilando os testes"
kotlinc -cp "$CP_APP:$SAIDA/main:$JUNIT" -d "$SAIDA/test" \
  "$APP/test/java/com/rafaelnm90/calorias/NutricaoTeste.kt"
echo "== rodando os testes"
java -cp "$CP_APP:$SAIDA/main:$SAIDA/test:$JUNIT" org.junit.runner.JUnitCore com.rafaelnm90.calorias.NutricaoTeste
