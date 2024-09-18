import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	// Requires JDK 17
	alias(libs.plugins.spring.boot) apply true
	alias(libs.plugins.spring.dependencymanagement) apply true
	alias(libs.plugins.kotlin.jvm) apply true
	alias(libs.plugins.kotlin.spring) apply true
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

	api(libs.langchain4j)
	implementation(libs.langchain4j.open.ai.spring.boot.starter)
	implementation(libs.langchain4j.embeddings.all.minilm.l6.v2)
	implementation(libs.langchain4j.weaviate)

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
