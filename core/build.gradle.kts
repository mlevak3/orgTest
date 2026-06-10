plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Čista JVM jezgra fiskalizacije: bez Android ovisnosti.
    // Testovi koriste JDK-ov javax.xml.crypto (XML-DSig) za validaciju potpisa.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}

tasks.test {
    testLogging { events("passed", "failed", "skipped") }
}
