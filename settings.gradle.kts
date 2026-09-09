// Repositorios de los complementos de compilacion. No se anade ninguno de terceros:
// el proyecto se compila con el complemento de Android y el compilador de Kotlin que este
// ya trae, y el JDK lo pone Android Studio (o JAVA_HOME) en la maquina que compila.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Los modulos no declaran repositorios propios: se resuelven todos desde aqui.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Menu08Movil"

// El modulo se llama `aplicacion`, no `app`: en este proyecto todo se nombra en espanol.
include(":aplicacion")
