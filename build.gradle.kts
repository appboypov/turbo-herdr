import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
    }
    implementation("org.tomlj:tomlj:1.1.1")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        changeNotes = """
            <p>0.1.1: terminal input fixes.</p>
            <ul>
              <li>The panel shows the latest screen after output stops, so a Herdr tab switch no longer leaves stale rows.</li>
              <li>Text, selection and the caret share one cell grid, so the caret no longer drifts on long lines.</li>
              <li>On macOS, Cmd+Left, Cmd+Right and Cmd+Backspace move to line start, line end and delete the line as in Ghostty; after the Herdr prefix only keys Herdr binds go to Herdr, and IDE shortcuts send nothing to the pane.</li>
            </ul>
            <p>0.1.0: first release.</p>
            <ul>
              <li>Herdr tool window with terminal emulation by libghostty-vt.</li>
              <li>Keys bound in Herdr's config win over IDE shortcuts while the panel has focus.</li>
              <li>Mouse, copy and paste; shared or per-project Herdr session.</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.environmentVariable("PUBLISH_CHANNEL").map { listOf(it) }.orElse(listOf("default"))
    }
    pluginVerification {
        ides {
            create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea, providers.gradleProperty("platformVersion"))
            recommended()
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}
