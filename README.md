# Po(AI)rot
![ Alt Text](logo.png)
Your friendly AI assistant to help you solve the murder of Olof Palme.

This application uses Retrieval-augmented generation (RAG) techniques and AI services together with specific (public) information about the murder case and relevant people that were either witnesses or suspects.

# Preconditions
For this application to execute, credentials from the external services OpenAPI and Weaviate are necessary.

## Weaviate
Go to weaviate.cloud (free option is available) and create a weaviate cluster and set the following properties in env.properties:
- `weaviate_url`
- `weaviate_api_key`

## OpenAPI
Use OpenAPI credentials and set the following in env.properties:
- `langchain4j.open-ai.chat-model.api-key`
- `organization_id`

# Instructions
- Check out project
- Create file env.properties and put it in `src/main/resources`
- Setup credentials in env.properties by

# Run the program
- Main class: [com.sundbybergsit.poairot.PoairotApplicationKt]
- Program argument is sent as a String, example: "Who killed Olof Palme?"
