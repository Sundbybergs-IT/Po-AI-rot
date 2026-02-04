package com.sundbybergsit.poairot.mcp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sundbybergsit.poairot.PoairotApplication
import io.modelcontextprotocol.server.McpAsyncServer
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransport
import io.modelcontextprotocol.spec.McpSchema
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse

@Configuration
@EnableWebFlux
class McpConfig(private val poairotApplication: PoairotApplication) {

    @Bean
    fun transport(): WebFluxSseServerTransport {
        val objectMapper = ObjectMapper()
        objectMapper.registerKotlinModule()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return WebFluxSseServerTransport(objectMapper, "/mcp/message")
    }

    @Bean
    fun router(transport: WebFluxSseServerTransport): RouterFunction<*> {
        println("Creating router bean")
        val routerFunction = transport.routerFunction
        println("Router function created: $routerFunction")
        return routerFunction
    }

    @Bean
    fun routerLogger(routerFunctions: List<RouterFunction<*>>): ApplicationListener<ApplicationReadyEvent> {
        return ApplicationListener {
            println("=== Registered Routes ===")
            routerFunctions.forEach { router ->
                println(router)
            }
        }
    }

    @Bean
    fun mcpServer(transport: WebFluxSseServerTransport): McpSyncServer  {
        val inputSchema = """
            {
              "type": "object",
              "properties": {
                "question": {
                  "type": "string",
                  "description": "The question to ask the detective"
                }
              },
              "required": ["question"]
            }
        """.trimIndent()

        val tool = McpSchema.Tool(
            "interrogate",
            "Interrogate the AI detective about the Olof Palme murder case",
            inputSchema
        )

        return McpServer.sync(transport)
            .serverInfo("Po-AI-rot", "1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .tool(tool) { arguments ->
                val question = arguments["question"] as String
                val answer = poairotApplication.interrogate(question)
                McpSchema.CallToolResult(
                    listOf(McpSchema.TextContent(answer)),
                    false
                )
            }
            .build()
    }
}
