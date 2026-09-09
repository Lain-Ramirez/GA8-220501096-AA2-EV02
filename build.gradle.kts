// Compilacion de la raiz. Solo declara el complemento de Android para que los modulos lo
// apliquen con su version ya fijada; aqui no se aplica (`apply false`).
plugins {
    alias(libs.plugins.android.application) apply false
}
