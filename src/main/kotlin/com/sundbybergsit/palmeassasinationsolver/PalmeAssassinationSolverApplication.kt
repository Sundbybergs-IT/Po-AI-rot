package com.sundbybergsit.palmeassasinationsolver

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentParser
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader
import dev.langchain4j.data.document.parser.TextDocumentParser
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiChatModelName
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter
import dev.langchain4j.rag.query.router.QueryRouter
import dev.langchain4j.service.AiServices
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths


@SpringBootApplication
class PalmeAssassinationSolverApplication

fun main(args: Array<String>) {
    runApplication<PalmeAssassinationSolverApplication>(*args)
    val polymath: Polymath = createPolymath()
    println(polymath.answer("Du är en talesperson för polisens kalla fall-grupp. Svara formellt med högst fyra meningar. ${args[0]}"))
}

private fun createPolymath(): Polymath {
    val chatModel: ChatLanguageModel = OpenAiChatModel.builder()
        .organizationId(System.getenv("organization_id"))
        .apiKey(System.getenv("open_ai_key"))
        .maxRetries(1)
        .modelName(OpenAiChatModelName.GPT_4_0613)
        .build()

    val embeddingModel: EmbeddingModel = AllMiniLmL6V2EmbeddingModel()

    val hearingContentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embed(toPath("/mop/txt/forhor/pol-1986-06-26-annette-kohut-forhor.txt"), embeddingModel))
        .embeddingModel(embeddingModel)
        .maxResults(10)
        .minScore(0.7)
        .build()

    val factsContentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embed(toPath("/mop/txt/facts.txt"), embeddingModel))
        .embeddingModel(embeddingModel)
        .maxResults(5)
        .minScore(0.6)
        .build()

    val retrieverToDescription: MutableMap<ContentRetriever, String> = HashMap()
    retrieverToDescription[hearingContentRetriever] = "hearings of Annette Kohut"
    retrieverToDescription[factsContentRetriever] = "facts about the murder of Olof Palme"
    val queryRouter: QueryRouter = LanguageModelQueryRouter(chatModel, retrieverToDescription)

    val retrievalAugmentor: RetrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryRouter(queryRouter)
        .build()

    return AiServices.builder(Polymath::class.java)
        .chatLanguageModel(chatModel)
        .retrievalAugmentor(retrievalAugmentor)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .build()
}


private fun embed(documentPath: Path, embeddingModel: EmbeddingModel): EmbeddingStore<TextSegment> {
    val documentParser: DocumentParser = TextDocumentParser()
    val document: Document = FileSystemDocumentLoader.loadDocument(documentPath, documentParser)

    val splitter = DocumentSplitters.recursive(300, 0)
    val segments = splitter.split(document)

    val embeddings: List<Embedding> = embeddingModel.embedAll(segments).content()

    val embeddingStore: EmbeddingStore<TextSegment> = InMemoryEmbeddingStore()
    embeddingStore.addAll(embeddings, segments)
    return embeddingStore
}

private fun toPath(fileName: String): Path {
    try {
        val fileUrl: URL = PalmeAssassinationSolverApplication::class.java.getResource(fileName)!!
        return Paths.get(fileUrl.toURI())
    } catch (e: URISyntaxException) {
        throw RuntimeException(e)
    }
}

internal interface Polymath {
    fun answer(query: String?): String?

}