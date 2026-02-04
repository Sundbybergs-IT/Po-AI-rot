import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
	alias(libs.plugins.spring.boot) apply true
	alias(libs.plugins.spring.dependencymanagement) apply true
	alias(libs.plugins.kotlin.jvm) apply true
	alias(libs.plugins.kotlin.spring) apply true
}

group = "com.sundbybergsit"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation(libs.spring.boot.starter.webflux)
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation(libs.jackson.module.kotlin)

	api(libs.langchain4j)
	implementation(libs.langchain4j.open.ai)
	implementation(libs.langchain4j.embeddings.all.minilm.l6.v2)
	implementation(libs.langchain4j.weaviate)
	implementation(libs.mcp.spring.webflux)

	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
	compilerOptions {
		freeCompilerArgs.add("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
