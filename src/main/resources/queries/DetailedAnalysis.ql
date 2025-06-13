// DetailedAnalysis.ql
import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.controlflow.Cfg
import semmle.code.java.controlflow.ControlFlowGraph
import SqlStrings // Import the predicate to identify SQL strings
import LogicalChunks // Import the logical chunk definitions

// Helper predicates (copy/paste from your original query, or make them common library)
predicate containsSql(StringLiteral lit) { SqlStrings.containsSql(lit) }
string getFrameworkContext(StringLiteral sql) { ... }
string normalizeSqlQuery(string original) { ... }
string escapeJson(string s) { ... }
string getSqlQueryType(StringLiteral sql, Expr context) { ... }
string extractParameters(StringLiteral sql) { ... }
string getMethodBody(Method m, StringLiteral sql) { ... }
string getDynamicSqlConsideration(StringLiteral sql) { ... }
string getUserInputConsideration(Method method) { ... }
string getSqlTypeConsideration(StringLiteral sql) { ... }

// --- IMPROVED HELPERS FOR CALL HIERARCHY AND DATA FLOW ---
// You would put the more robust call_hierarchy and data_flow predicates here.
// For example, data flow from method parameters to the SQL string.
string getCallHierarchyEntries(Method sqlMethod) {
  // This is where you would build the JSON array of callers.
  // This is still challenging to make deeply recursive and output directly as a single JSON string.
  // A better approach for deep hierarchy might be to return a BQRS of (caller, callee, depth)
  // and construct the full graph in your Spring Boot app.
  result = "[" +
    concat(Method m, int depth |
      // Start with the method containing SQL
      (m = sqlMethod and depth = 0) or
      // Direct callers
      exists(MethodAccess ma | ma.getMethod() = sqlMethod and m = ma.getEnclosingCallable() and depth = 1) or
      // Callers of direct callers (depth 2)
      exists(MethodAccess ma1, MethodAccess ma2 | ma1.getMethod() = sqlMethod and ma2.getMethod() = ma1.getEnclosingCallable() and m = ma2.getEnclosingCallable() and depth = 2)
      // Add more depths or use a recursive predicate if performance allows and JSON output structure is simple enough
    |
      "{ \"method\": \"" + escapeJson(m.getDeclaringType().getName() + "." + m.getName()) + "\", " +
      "\"file\": \"" + escapeJson(m.getFile().getBaseName()) + "\", " +
      "\"line\": " + m.getLocation().getStartLine() + ", " +
      "\"depth\": " + depth + " }", ",\n")
  + "]"
}

string getInputsToSql(StringLiteral sql) {
    // This is where you would implement robust data flow.
    // For example, finding parameters that flow into the SQL string.
    exists(DataFlow::Node source, DataFlow::Node sink |
        sink.asExpr() = sql and
        DataFlow::localFlow(source, sink)
    |
        result = "[\"" + concat(DataFlow::Node n |
            sink.asExpr() = sql and DataFlow::localFlow(n, sink) and not n.asExpr() = sql // Exclude the literal itself
            | escapeJson(n.toString()), "\", \"") + "\"]"
    ) or (not exists(DataFlow::Node source, DataFlow::Node sink | sink.asExpr() = sql and DataFlow::localFlow(source, sink)) and result = "[]")
}


from StringLiteral sql, Method method, Type declaringType, Method logicalChunkMethod
where
  containsSql(sql) and
  method = sql.getEnclosingCallable() and
  declaringType = method.getDeclaringType() and
  logicalChunkMethod = LogicalChunks.getLogicalChunkMethod(sql) and // Get the logical chunk method
  not sql.getFile().getAbsolutePath().matches("%test%") and
  not sql.getFile().getAbsolutePath().matches("%generated%")
select
  // Output in CSV format for easier processing in Spring Boot
  // Each field separated by a delimiter, and potentially escaped
  escapeJson(logicalChunkMethod.getDeclaringType().getName() + "." + logicalChunkMethod.getName()), // Logical Chunk ID
  escapeJson(sql.getFile().getAbsolutePath()),
  sql.getLocation().getStartLine().toString(),
  escapeJson(declaringType.getName()),
  escapeJson(method.getName()),
  escapeJson(method.getSignature()),
  escapeJson(getMethodBody(method, sql)),
  escapeJson(getSqlQueryType(sql, sql.getParent())),
  escapeJson(sql.getValue()),
  escapeJson(normalizeSqlQuery(sql.getValue())),
  extractParameters(sql), // This is already JSON array
  getCallHierarchyEntries(method), // This is already JSON array
  getInputsToSql(sql), // This is already JSON array
  escapeJson(getFrameworkContext(sql)),
  "[\"" + concat(string consideration |
    consideration = getDynamicSqlConsideration(sql) or
    consideration = getUserInputConsideration(method) or
    consideration = getSqlTypeConsideration(sql)
  | consideration, "\", \"") + "\"]" // Considerations as JSON array