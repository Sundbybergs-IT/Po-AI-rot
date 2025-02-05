package com.sundbybergsit.poairot

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentParser
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader
import dev.langchain4j.data.document.parser.TextDocumentParser
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
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
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap


@SpringBootApplication
class PoairotApplication

fun main(args: Array<String>) {
    runApplication<PoairotApplication>(*args)
    val envProperties = checkNotNull(loadProperties())
    val polymath: Polymath = createPolymath(
        envProperties.getProperty("organization_id"),
        envProperties.getProperty("openai_api_key"),
        envProperties.getProperty("weaviate_api_key"),
        envProperties.getProperty("weaviate_url")
    )
    println(
        polymath.answer(
            "Du är talesperson för polisens kalla fall-grupp och antar personan av Hercule Poirot, " +
                    "känd för sitt detaljerade och noggranna detektivarbete. " +
                    "Du använder en teatralisk ton liknande Poirots, men ditt språk är övervägande svenska. " +
                    "Du kan inkludera några sporadiska franska fraser för att förstärka karaktären, " +
                    "men huvuddelen av kommunikationen och all teknisk information om utredningen av mordet på Olof Palme ska vara på svenska. " +
                    "Kom ihåg att all information om fallet är offentlig och ska hanteras korrekt. När du svarar på frågan: '${args[0]}', " +
                    "analysera och tolka den med fokus på detta mordfall och använd relevanta detaljer och fakta för att ge ett trovärdigt och informativt svar."
        )
    )
}

fun loadProperties(): Properties? {
    val prop = Properties()
    val classLoader = Thread.currentThread().contextClassLoader
    classLoader.getResourceAsStream("env.properties").use { input ->
        if (input == null) {
            println("Sorry, unable to find env.properties")
            return null
        }

        prop.load(input)
        return prop
    }
}

private fun createPolymath(
    openAiOrganizationId: String,
    openAiApiKey: String,
    weaviateApiKey: String,
    weaviateUrl: String,
): Polymath {
    val chatModel: ChatLanguageModel = OpenAiChatModel.builder()
        .organizationId(openAiOrganizationId)
        .apiKey(openAiApiKey)
        .maxRetries(2)
        .modelName(OpenAiChatModelName.GPT_4)
        .build()

    val embeddingModel: EmbeddingModel = AllMiniLmL6V2EmbeddingModel()

    val weaviateEmbeddingStore = WeaviateEmbeddingStore.builder()
        .apiKey(weaviateApiKey)
        .scheme("https")
        .host(weaviateUrl)
        .build()

    val retrieverToDescription: MutableMap<ContentRetriever, String> = HashMap<ContentRetriever, String>().apply {
        embedPersonsOfInterest(embeddingModel, weaviateEmbeddingStore)
        embedFacts(embeddingModel, weaviateEmbeddingStore)
        embedProtocols(embeddingModel, weaviateEmbeddingStore)
        embedHearings(embeddingModel, weaviateEmbeddingStore)
        embedCommissions(embeddingModel, weaviateEmbeddingStore)
        embedMemos(embeddingModel, weaviateEmbeddingStore)
    }

    val queryRouter: QueryRouter = LanguageModelQueryRouter(chatModel, retrieverToDescription)

    val retrievalAugmentor: RetrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryRouter(queryRouter)
        .build()

    return AiServices.builder(Polymath::class.java)
        .chatLanguageModel(chatModel)
        .retrievalAugmentor(retrievalAugmentor)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(1))
        .build()
}

private fun MutableMap<ContentRetriever, String>.embedCommissions(
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
) {
    for (kommission in getAll(
        Triple(
            "Granskningskommissionens betänkande i anledning av Brottsutredningen efter mordet på statsminister Olof Palme",
            "/mop/txt/kommission/grk.txt",
            "https://www.regeringen.se/rattsliga-dokument/statens-offentliga-utredningar/1999/01/sou-199988--/"
        ),
        embeddingModel = embeddingModel,
        weaviateEmbeddingStore = weaviateEmbeddingStore,
        maxResults = 5,
        minScore = 0.7,
    )) {
        this[kommission.second] = "${kommission.first}. Källa: ${kommission.third}"
    }
}

private fun MutableMap<ContentRetriever, String>.embedProtocols(
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
) {
    for (protocol in getAll(
        Triple(
            "protokoll skrivet av Brigitta Brolund. Källa: pol-1986-02-28-Anteckningar-Brigitta-Brolund-ledningscentralen",
            "/mop/txt/protokoll/pol-1986-02-28-Anteckningar-Brigitta-Brolund-ledningscentralen.txt",
            "pol-1986-02-28-Anteckningar-Brigitta-Brolund-ledningscentralen"
        ),
        Triple(
            "Obduktionsprotokollet 1 mars 1986",
            "/mop/txt/protokoll/obduktionsprotokollet.txt",
            "D:nr F 719/86"
        ),
        embeddingModel = embeddingModel,
        weaviateEmbeddingStore = weaviateEmbeddingStore,
        maxResults = 20,
        minScore = 0.7,
    )) {
        this[protocol.second] =
            "polisförhör av ${protocol.first} med anledning av mordet på Olof Palme. Källa: ${protocol.third}"
    }
}

private fun MutableMap<ContentRetriever, String>.embedHearings(
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
) {
    for (hearing in getAll(
        Triple(
            "Lars Jepsson 1 mars 1986",
            "/mop/txt/forhor/pol-E15-00-Lars-Jeppsson-1986-03-01.txt",
            "pol-E15-00-Lars-Jeppsson-1986-03-01"
        ),
        Triple(
            "Christer Andersson 1 november 1994",
            "/mop/txt/forhor/pol-1994-01-11-forhor-christer-a.txt",
            "pol-1994-01-11-forhor-christer-a"
        ),
        Triple(
            "Christer Andersson 1 november 1994",
            "/mop/txt/forhor/pol-1994-01-11-forhor-christer-a-2.txt",
            "pol-1994-01-11-forhor-christer-a-2"
        ),
        Triple(
            "Annette Kohut 19 mars 1986",
            "/mop/txt/forhor/pol-1986-03-19-anette-kohut-forsta-forhor.txt",
            "pol-1986-03-19-anette-kohut-forsta-forhor"
        ),
        Triple(
            "Annette Kohut 26 juni 1986",
            "/mop/txt/forhor/pol-1986-06-26-annette-kohut-forhor.txt",
            "pol-1986-06-26-annette-kohut-forhor"
        ),
        Triple(
            "Lars Jeppsson 1 mars 1986",
            "/mop/txt/forhor/pol-E15-00-Lars-Jeppsson-1986-03-01.txt",
            "pol-E15-00-Lars-Jeppsson-1986-03-01"
        ),
        Triple(
            "Lars Jeppsson 4 mars 1986",
            "/mop/txt/forhor/pol-E15-00-A-Lars-Jeppsson-1986-03-04.txt",
            "pol-E15-00-Lars-Jeppsson-1986-03-04"
        ),
        Triple(
            "Anders Delsborn 1 mars 1986",
            "/mop/txt/forhor/VITTNESFÖRHÖR-Anders-Delsborn-1986-03-01.txt",
            "VITTNESFÖRHÖR-Anders-Delsborn-1986-03-01"
        ),
        Triple(
            "Anders Björkman 1 mars 1986",
            "/mop/txt/forhor/pol-E13-A-Björkman-Anders-1986-03-01.txt",
            "pol-E13-A-Björkman-Anders-1986-03-01"
        ),
        Triple(
            "Anders Björkman 25 mars 1987",
            "/mop/txt/forhor/pol-E13-C-Björkman-Anders-1987-03-25.txt",
            "pol-E13-C-Björkman-Anders-1987-03-25"
        ),
        Triple(
            "Inge Morelius 1 mars 1986",
            "/mop/txt/forhor/Pol-1986-03-01_0920_E107-00_Förhör_med_Inge_Morelius.txt",
            "Pol-1986-03-01_0920_E107-00_Förhör_med_Inge_Morelius"
        ),
        embeddingModel = embeddingModel,
        weaviateEmbeddingStore = weaviateEmbeddingStore,
        maxResults = 30,
        minScore = 0.7,
    )) {
        this[hearing.second] =
            "polisförhör av ${hearing.first} med anledning av mordet på Olof Palme. Källa: ${hearing.third}"
    }
}

private fun MutableMap<ContentRetriever, String>.embedPersonsOfInterest(
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
) {
    for (person in getAll(
        Triple("mördaren", "/mop/txt/personer/mordaren.txt", ""),
        Triple("Carl Gustav Östling", "/mop/txt/personer/carl-gustav-östling.txt", ""),
        Triple("Christer Pettersson", "/mop/txt/personer/christer-pettersson.txt", ""),
        Triple("Christer Andersson", "/mop/txt/personer/christer-andersson.txt", ""),
        Triple("Anders Björkman", "/mop/txt/personer/anders-bjorkman.txt", ""),
        Triple("Lars Jeppsson", "/mop/txt/personer/lars-jeppsson.txt", ""),
        Triple("Lars Krantz", "/mop/txt/personer/lars-krantz.txt", ""),
        Triple("Lisbeth Palme", "/mop/txt/personer/lisbeth-palme.txt", ""),
        Triple("Anders Delsborn", "/mop/txt/personer/anders-delsborn.txt", ""),
        Triple("Inge Morelius", "/mop/txt/personer/inge-morelius.txt", ""),
        Triple("Olof Palme", "/mop/txt/personer/olof-palme.txt", ""),
        embeddingModel = embeddingModel,
        weaviateEmbeddingStore = weaviateEmbeddingStore,
        maxResults = 25,
        minScore = 0.6,
    )) {
        this[person.second] = "Allmänna faktauppgifter om ${person.first}"
    }
}

private fun MutableMap<ContentRetriever, String>.embedMemos(
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
) {
    for (proMemoria in getAll(
        Triple(
            "Uppföljning av Engström",
            "/mop/txt/pm/pol-1987-02-09-e-63-1-pm-uppfoljning-av-engstrom-o.txt",
            "ol-1987-02-09-e-63-1"
        ),
        Triple("Första observation av buss 43", "/mop/txt/pm/pol-1986-03-03-EAE-340.txt", "pol-1986-03-03-EAE-340"),
        Triple(
            "Lars Krantz känner igen mannen på buss 43",
            "/mop/txt/pm/pol-1986-03-03-EAE-340-B.txt",
            "pol-1986-03-03-EAE-340-B"
        ),
        embeddingModel = embeddingModel,
        weaviateEmbeddingStore = weaviateEmbeddingStore,
        maxResults = 25,
        minScore = 0.6,
    )) {
        this[proMemoria.second] = "polis-promemoria om mordet på Olof Palme. ${proMemoria.first}"
    }
}

private fun MutableMap<ContentRetriever, String>.embedFacts(
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
) {
    for (fact in getAll(
        Triple("Fakta om mordet", "/mop/txt/facts.txt", "Ingen särskild källa, allmänna uppgifter"),
        embeddingModel = embeddingModel,
        weaviateEmbeddingStore = weaviateEmbeddingStore,
        maxResults = 10,
        minScore = 0.6,
    )) {
        this[fact.second] = "fakta om mordet på Olof Palme. ${fact.first}"
    }
}

fun getAll(
    vararg titleFileNameTriples: Triple<String, String, String>,
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
    maxResults: Int,
    minScore: Double,
): List<Triple<String, ContentRetriever, String>> {
    val result: MutableList<Triple<String, ContentRetriever, String>> = mutableListOf()
    for (titleFileNameTriple in titleFileNameTriples) {
        result.add(
            Triple(
                titleFileNameTriple.first,
                EmbeddingStoreContentRetriever.builder()
                    .displayName("EmbStoreRetriever: ${titleFileNameTriple.first}")
                    .embeddingStore(embed(toPath(titleFileNameTriple.second), embeddingModel, weaviateEmbeddingStore))
                    .embeddingModel(embeddingModel)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build(),
                titleFileNameTriple.third
            )
        )
    }
    return result
}

private fun embed(
    documentPath: Path,
    embeddingModel: EmbeddingModel,
    embeddingStore: EmbeddingStore<TextSegment>
): EmbeddingStore<TextSegment> {
    val documentParser: DocumentParser = TextDocumentParser()
    val document: Document = FileSystemDocumentLoader.loadDocument(documentPath, documentParser)
    val splitter = DocumentSplitters.recursive(300, 0)
    val segments = splitter.split(document)
    val embeddings: List<Embedding> = embeddingModel.embedAll(segments).content()
    embeddingStore.addAll(embeddings, segments)
    return embeddingStore
}

private fun toPath(fileName: String): Path {
    try {
        val fileUrl: URL = PoairotApplication::class.java.getResource(fileName)!!
        return Paths.get(fileUrl.toURI())
    } catch (e: URISyntaxException) {
        throw RuntimeException(e)
    }
}

internal interface Polymath {

    fun answer(query: String?): String?

}