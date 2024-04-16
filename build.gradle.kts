import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	// Requires JDK 17
	id("org.springframework.boot") version "3.2.2"
	id("io.spring.dependency-management") version "1.1.4"
	kotlin("jvm") version "1.9.22"
	kotlin("plugin.spring") version "1.9.22"
}

group = "com.sundbybergsit"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	api("dev.langchain4j:langchain4j:0.30.0")
	implementation("dev.langchain4j:langchain4j-open-ai-spring-boot-starter:0.30.0")
	implementation("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:0.30.0")
	implementation("dev.langchain4j:langchain4j-weaviate:0.30.0")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
