// LogicalChunks.ql
import java
import SqlStrings // Import the previous query/library

// Define what constitutes a "logical chunk".
// For example, a logical chunk could be defined by a "top-level" method
// (e.g., a method that is a controller endpoint, a service method, etc.)
// You'll need to refine this based on your application's architecture.
predicate isTopLevelMethod(Method m) {
    // Example: Methods in controller packages
    m.getDeclaringType().getPackage().getName().matches("%controller%") or
    // Example: Methods annotated with @Service, @RestController, etc.
    exists(Annotation a | a.getAnAnnotatedElement() = m |
        a.getType().hasQualifiedName("org.springframework.stereotype", "Service") or
        a.getType().hasQualifiedName("org.springframework.web.bind.annotation", "RestController")
    )
    // Add more patterns relevant to your application's entry points
}

// Helper to get the "logical chunk" method for a given SQL string
Method getLogicalChunkMethod(StringLiteral sql) {
    exists(Method m |
        m = sql.getEnclosingCallable() and isTopLevelMethod(m)
    )
    or // If the direct enclosing callable is not top-level, trace back to a top-level caller
    exists(Method sqlMethod, Method currentMethod, Method topLevelMethod |
        sqlMethod = sql.getEnclosingCallable() and
        // Find a path from the SQL's method to a top-level method
        topLevelMethod.polyCalls*(currentMethod) and
        currentMethod = sqlMethod and
        isTopLevelMethod(topLevelMethod)
    |
        result = topLevelMethod
    )
    // Fallback: if no top-level method found, use the enclosing method as a chunk
    or (
        not exists(getLogicalChunkMethod(sql)) and
        result = sql.getEnclosingCallable()
    )
}

from StringLiteral sql, Method chunkMethod
where containsSql(sql) and chunkMethod = getLogicalChunkMethod(sql)
select
  sql.toString(), // A unique ID for the string literal (its textual representation + location)
  escapeJson(chunkMethod.getDeclaringType().getName() + "." + chunkMethod.getName()), // Logical Chunk ID
  escapeJson(sql.getFile().getAbsolutePath()),
  sql.getLocation().getStartLine(),
  escapeJson(sql.getValue())