plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    // SAST: поиск дефектов и уязвимостей в байт-коде проекта
    id("com.github.spotbugs") version "6.5.11"
    // SCA: проверка зависимостей на известные CVE
    id("org.owasp.dependencycheck") version "12.2.2"
}

group = "org.example"
version = "0.0.1-SNAPSHOT"
description = "ib-lab-1"

// Spring Boot 4.1.1 тянет Tomcat 11.0.24 с критическими CVE (CVE-2026-65905, CVE-2026-65637
// и др.), поэтому управляемая версия поднята до ближайшей исправленной.
ext["tomcat.version"] = "11.0.26"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Детекторы уязвимостей для SpotBugs (Find Security Bugs)
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    // В 20240325.1 — CVE-2025-66021, исправлено в более свежем выпуске.
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20260313.1")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-gson:0.12.3")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// SAST: анализ байт-кода SpotBugs + Find Security Bugs.
spotbugs {
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.LOW
    excludeFilter = file("config/spotbugs/exclude.xml")
    ignoreFailures = false
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    // Собственные санитайзеры проекта, чтобы taint-анализ не считал очищенные данные опасными.
    jvmArgs = listOf(
        "-Dfindsecbugs.taint.customconfigfile=" +
            file("config/spotbugs/find-sec-bugs-taint.txt").absolutePath
    )
    reports.create("html") {
        required = true
        setStylesheet("fancy-hist.xsl")
    }
    reports.create("sarif") {
        required = true
    }
}

/**
 * Ключ NVD для Dependency-Check. Берётся, в порядке приоритета, из переменной окружения
 * NVD_API_KEY (так он приходит из секрета репозитория в CI), из -PnvdApiKey или из
 * строки NVD_API_KEY в файле .env, который лежит в .gitignore и не попадает в репозиторий.
 */
fun nvdApiKey(): String? {
    System.getenv("NVD_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }
    (findProperty("nvdApiKey") as String?)?.takeIf { it.isNotBlank() }?.let { return it }

    val envFile = file(".env")
    if (!envFile.exists()) {
        return null
    }
    return envFile.readLines()
        .firstOrNull { it.trimStart().startsWith("NVD_API_KEY=") }
        ?.substringAfter('=')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

// SCA: проверка зависимостей на известные CVE.
dependencyCheck {
    formats = listOf("HTML", "JSON")
    // Сборка падает, если найдена уязвимость уровня High или Critical.
    failBuildOnCVSS = 7.0f
    // Ключ NVD не обязателен, но без него обновление базы сильно ограничено по скорости.
    // Пустое значение передавать нельзя — сканер считает его некорректным ключом.
    nvdApiKey()?.let { nvd.apiKey = it }
    suppressionFile = "config/dependency-check/suppressions.xml"
    scanConfigurations = listOf("runtimeClasspath")
    skipTestGroups = false
    // Анализатор .NET-сборок проекту не нужен.
    analyzers.assemblyEnabled = false
}
