# About the project

The core of this process is to **analyze** the entire codebase once, **group** the required changes by file with
additional support files, and then loop through each file, letting the LLM perform a **batch conversion** on it before
committing the changes and moving to the next.

## 🏛️ Main Components

### Project Structure

- **Application Layer** – expose services (use cases)
- **Domain Layer** – contains business logic
- **Infrastructure Layer** – handles Git, Docker, etc.
- **Adapter Layer** – manages inputs (CLI, REST API, etc.)

### 🛠️ Tools

- **🔎 CodeQL:**
    - **Function:** Static code analyzer.
    - **What it does:** "Reads" a file or project to find specific code patterns.
    - **Output:** Relevant code snippets and their context.
- **⚙️ Execution Tool:**
    - **Function:** Compiler.
    - **What it does:** Attempts to compile a code file.
    - **Output:** Success/Failure status and result logs.
- **📄 Patch Generator:**
    - **Function:** Code diff generator.
    - **What it does:** Compares the original version of a file with the version modified by the agents.
    - **Output:** A `.patch` file ready for human review and application.

### 🤖 Agents

- **🤖 Dev AI:**
    - **Function:** Generate or modify code.
    - **Skills:**
        1. **Convert:** Takes code (e.g., DB2) and rewrites it in another format (e.g., PostgreSQL).
- **🤖 QA AI:**
    - **Function:** Review and validate Dev AI's output.
    - **Skills:**
        1. **Validate Build:** Uses the `Execution Tool` to confirm that the modified code still compiles.

## 🌊 Workflows

### Workflow 1: Database Conversion

**Goal:** Convert DB2 code to PostgreSQL and ensure it still compiles.

1. **🔎 CodeQL** ➡️ Analyzes the project and finds code with DB2 syntax.
2. **🤖 Dev AI** ➡️ Receives the code and converts it to PostgreSQL syntax.
3. **🤖 QA AI** ➡️ Takes the converted code and uses the Execution Tool to verify that the file compiles.
4. **✅ Sucesso** ➡️ The `Patch Generator` creates a `.patch` file with the validated conversion.

### Detailed Workflow

### Step 1: Initial Setup & Global Analysis

1. **Clone/Use ZIP Repository:** The application starts by cloning the client's Git repository to a local workspace.
2. **Run CodeQL Analysis:** It executes a single, comprehensive CodeQL query across the entire codebase. This query is
   designed to find every database sink and trace all of its data sources. The output isn't just text; it's a structured
   table of results that you'll export to JSON.

### Step 2: Post-Processing & Grouping

1. **Decode to JSON:** The binary CodeQL result file (`.bqrs`) is decoded into a structured JSON file.
2. **Group by File:** The Spring Boot application parses this JSON. It iterates through every result (every
   source-to-sink path) and groups them into a `Map`, where the **key is the file path** (`String`) and the **value is a
   list of all conversion units** (`List<ConversionUnit>`) found within that file. This is the central "batching" step.

### Step 3: The Conversion Loop

The application now iterates through the map of grouped findings, processing one file at a time.

1. **Assemble LLM Prompt:** For a single file, your application gathers all its associated `ConversionUnit` objects and
   assembles them into one large, structured JSON request for the LLM. This prompt clearly instructs the LLM that all
   the included units are for the same file and should be converted together.
2. **Send to LLM:** The request is sent to the LLM.
3. **Receive Unified Patch:** The LLM processes the entire request and returns a single JSON response. This response
   contains a list of "patch" objects, each specifying a precise location in the file and the new code to insert. The
   LLM is responsible for ensuring the coordinates in this unified patch are consistent with each other.

### Step 4: Apply Changes & Finalize

1. **Apply Patches:** The application reads the list of patch objects and applies each one sequentially to the target
   file.
2. **Build:** It builds only the modified module to ensure the changes haven't broken the build.
3. **Commit:** If the build is successful, the application commits the modified file to Git. The LLM can generate the
   commit message, summarizing all the changes made within that file (e.g., "Refactor: Convert 3 DB2 queries to
   PostgreSQL in UserDAO.java").
4. **Repeat:** The loop continues to the next file in the map until all have been processed.

## Desired Output

---

### Spring Boot's Internal Data Model

**`raw-results.json`** (The direct output from CodeQL)

```json
{
  "results": [
    {
      "id": "/app/project/src/main/java/org/example/employee/EmployeeRepository.java:47:22:47:78",
      "path": "/app/project/src/main/java/org/example/employee/EmployeeRepository.java",
      "startLine": 47,
      "startColumn": 22,
      "endLine": 47,
      "endColumn": 78,
      "methodName": "getFirstFiveEmployees",
      "code": "SELECT * FROM Employees FETCH FIRST 5 ROWS ONLY WITH UR",
      "sourceExpressionType": "String",
      "className": "EmployeeRepository",
      "type": "STATIC"
    }
    // ... more results ...
  ]
}
```

**`CodeQlResult.java`** (Represents one row from the CodeQL output. AKA the results.json file)

```java
public class CodeQlResult {
    private String id;
    private String path;
    private int startLine;
    private int startColumn;
    private int endLine;
    private int endColumn;
    private String methodName;
    private String code;
    private String sourceExpressionType;
    private String className;
    private String type;
}
```

After parsing, it groups these findings into a `Map<String, List<CodeQlResult>>` where the
key is `filePath`.

### 3. The Final LLM Prompt JSON (structured_tasks.json)

This is the most critical data structure. The application assembles this JSON to send to the LLM. It's designed for
maximum clarity.

```json
[
  {
    "sink": {
      "filePath": "src/main/java/com/mycompany/UserDAO.java",
      "conversionUnits": [
        {
          "unitId": "com.mycompany.UserDAO.findUserById",
          "components": [
            {
              "code": "SELECT ",
              "type": "StringLiteral",
              "location": {
                "startLine": 12,
                "startColumn": 25,
                "endLine": 12,
                "endColumn": 33
              }
            },
            {
              "code": "SqlConstants.USER_COLUMNS",
              "type": "FieldAccess",
              "location": {
                "startLine": 12,
                "startColumn": 36,
                "endLine": 12,
                "endColumn": 61
              }
            },
            {
              "code": " FROM users WHERE user_id = ? FETCH FIRST 1 ROWS ONLY",
              "type": "StringLiteral",
              "location": {
                "startLine": 12,
                "startColumn": 64,
                "endLine": 12,
                "endColumn": 118
              }
            }
          ]
        },
        {
          "unitId": "com.mycompany.UserDAO.getActiveUsers",
          "components": [
            // ... components for another query in the same file ...
          ]
        }
      ]
    }
  }
]
```

### 4. The LLM Response JSON (converted_sql.json)

The LLM should be instructed to return a clean JSON object containing a list of edits to be made.

```json
[
  {
    "explanation": "Refactor(DAO): Convert findUserById and getActiveUsers to PostgreSQL syntax.",
    "file": "src/main/java/com/mycompany/UserDAO.java",
    "replacements": [
      {
        "location": {
          "startLine": 12,
          "startColumn": 25,
          "endLine": 12,
          "endColumn": 118
        },
        "convertedCode": "\"SELECT \" + SqlConstants.USER_COLUMNS + \" FROM users WHERE user_id = ? LIMIT 1\""
      },
      {
        "location": {
          "startLine": 35,
          "startColumn": 25,
          "endLine": 35,
          "endColumn": 95
        },
        "convertedCode": "\"SELECT * FROM users WHERE status = 'ACTIVE' ORDER BY creation_date\""
      }
    ]
  }
]
```

The application then simply iterates through the `patches` array and applies the changes, confident that the process is
robust and the coordinates are valid for the scope of this single-file operation.

## Environment Setup

### Requirements

- Java 21
- Maven
- Docker
- Access to OpenAI API or another LLM provider

Create a `application.properties` file in the `src/main/resources` directory of your project with the following content:

```
spring.application.name=source-code-converter
spring.servlet.multipart.max-file-size=500MB
spring.servlet.multipart.max-request-size=500MB

codeql.docker.image-name=codeql-runner-image
codeql.docker.container-name=temp-code-converter
codeql.docker.dockerfile-dir=C:/your-path-to-project/t-systems/source-code-converter/src/codeql-docker
codeql.db.persist-volume=true
codeql.db.volume-name=codeql-test-db-volume

# AIE-Common-Core Configuration
aie.project=AMS4TRUCK
aie.username=your_username
aie.password=your_password

# Files paths for the conversion process
conversion.output.directory=src/main/resources/output
conversion.output.results-json=results.json
conversion.output.structured-tasks-json=structured_tasks.json
conversion.output.converted-sql-json=converted_sql.json
conversion.output.patch-diff=patch.diff
conversion.output.patches-directory=src/main/resources/output/patches
```

## Running the Application

### **Important Note:** If not using the IBL project as a test case, please comment  the `" --command=/opt/custom_build.sh" +` inside the `CodeQlRunner.java` file, line 29 and the `COPY ./custom_build.sh /opt/custom_build.sh RUN chmod +x /opt/custom_build.sh` inside the `Dockerfile` located in `src/codeql-docker` folder.

1. **Build the Project:**
   Navigate to the project directory and run:
   ```bash
   mvn clean install
   ```
2. **Run the Application:**
    ```bash
    mvn spring-boot:run
     ```
3. **Request List on Postman:**
    1. Send a GET request to (to start the conversion process generating logs and results.json):
       ```bash
       # you can use the git repo url as example `https://github.com/EduardoOrthmann/janus-test-project.git`
       http://localhost:8080/api/conversion-logs?repoUrl=<the-git-repo-url>
       ```
       Or send a POST request with a zip file:
       ```bash
       http://localhost:8080/api/conversion-logs-zip
       ```
       Body:
       ```
       form-data
       Key: file
       Type: File
       Value: (Select your zip file)
       ```
   
    2. Send a GET request to (it shows the results from results.json):
       ```
       http://localhost:8080/api/results
       ```
       
    3. Send a POST request to (to convert the SQL code found in results.json and generate converted_sql.json):
       ```bash
       http://localhost:8080/api/convert
       ```
       
    4. Send a POST request to (to generate the patch.diff file from converted_sql.json):
       ```bash
       http://localhost:8080/api/generate-patch
       ```