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
    println(polymath.answer("Du är en talesperson för polisens kalla fall-grupp och talar som Hercule Poirot och lägger in franska ord då och då men mestadels svarar du på svenska. Svara formellt och utelämna inga detaljer. ${args[0]}"))
}

private fun createPolymath(): Polymath {
    val chatModel: ChatLanguageModel = OpenAiChatModel.builder()
        .organizationId(System.getenv("organization_id"))
        .apiKey(System.getenv("open_ai_key"))
        .maxRetries(1)
        .modelName(OpenAiChatModelName.GPT_4_0613)
        .build()

    val embeddingModel: EmbeddingModel = AllMiniLmL6V2EmbeddingModel()

    val brigittaBrolundProtocolContentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(
            embed(
                toPath("/mop/txt/protokoll/pol-1986-02-28-Anteckningar-Brigitta-Brolund-ledningscentralen.txt"),
                embeddingModel
            )
        )
        .embeddingModel(embeddingModel)
        .maxResults(10)
        .minScore(0.7)
        .build()

    val proMemoriaContentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(
            embed(
                toPath("/mop/txt/pm/pol-1987-02-09-e-63-1-pm-uppfoljning-av-engstrom-o.txt"),
                embeddingModel
            )
        )
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
    for (hearing in getHearings(
        Pair("Lars Jepsson", "/mop/txt/forhor/pol-E15-00-Lars-Jeppsson-1986-03-01.txt"),
        Pair("Christer Andersson", "/mop/txt/forhor/pol-1994-01-11-forhor-christer-a.txt"),
        Pair("Annette Kohut", "/mop/txt/forhor/pol-1986-03-19-anette-kohut-forsta-forhor.txt"),
        Pair("Annette Kohut", "/mop/txt/forhor/pol-1986-06-26-annette-kohut-forhor.txt"),
        Pair("Lars Jeppsson", "/mop/txt/forhor/pol-E15-00-Lars-Jeppsson-1986-03-01.txt"),
        Pair("Anders Björkman", "/mop/txt/forhor/pol-E13-A-Björkman-Anders-1986-03-01.txt"),
        Pair("Inge Morelius", "/mop/txt/forhor/Pol-1986-03-01_0920_E107-00_Förhör_med_Inge_Morelius.txt"),
        embeddingModel = embeddingModel
    )) {
        retrieverToDescription[hearing.second] = "polisförhör av ${hearing.first} med anledning av mordet på Olof Palme"
    }

    for (person in getPersons(
        Pair("mördaren", "/mop/txt/personer/mordaren.txt"),
        Pair("den dömde misstänkte Christer Pettersson", "/mop/txt/personer/christer-pettersson.txt"),
        Pair("den misstänkte Christer Andersson", "/mop/txt/personer/christer-andersson.txt"),
        Pair("mordvittnet Anders Björkman", "/mop/txt/personer/anders-bjorkman.txt"),
        Pair("mordvittnet Lars Jeppsson", "/mop/txt/personer/lars-jeppsson.txt"),
        Pair("mordvittnet Inge Morelius", "/mop/txt/personer/inge-morelius.txt"),
        embeddingModel = embeddingModel
    )) {
        retrieverToDescription[person.second] = "facts about ${person.first}"
    }

    retrieverToDescription[brigittaBrolundProtocolContentRetriever] = "protokoll skrivet av Brigitta Brolund"
    retrieverToDescription[factsContentRetriever] = "fakta om mordet på Olof Palme"
    retrieverToDescription[proMemoriaContentRetriever] = "polis-promemoria om mordet på Olof Palme"
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

fun getPersons(vararg titleFileNamePairs: Pair<String, String>, embeddingModel: EmbeddingModel): List<Pair<String, ContentRetriever>> {
    val result: MutableList<Pair<String, ContentRetriever>> = mutableListOf()
    for (titleFileNamePair in titleFileNamePairs) {
        result.add(Pair(titleFileNamePair.first,
            EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embed(toPath(titleFileNamePair.second), embeddingModel))
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.6)
                .build())
        )
    }
    return result
}

fun getHearings(vararg titleFileNamePairs: Pair<String, String>, embeddingModel: EmbeddingModel): List<Pair<String, ContentRetriever>> {
    val result: MutableList<Pair<String, ContentRetriever>> = mutableListOf()
    for (titleFileNamePair in titleFileNamePairs) {
        result.add(Pair(titleFileNamePair.first,
            EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embed(toPath(titleFileNamePair.second), embeddingModel))
                .embeddingModel(embeddingModel)
                .maxResults(10)
                .minScore(0.7)
                .build())
        )
    }
    return result
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