/**
 * * @name Detect All Database Code and its Sources
 * * @description This query finds all database sinks (standard JDBC and custom framework calls)
 * * and traces all of their data sources. It is specifically designed to handle frameworks
 * * that use static constants as query identifiers and case-insensitivity.
 * * @kind path-problem
 * * @id java/db-migration-source-sink-analysis
 * * @tags db-migration java jdbc
 * 
 */

import java
import semmle.code.java.dataflow.TaintTracking
import semmle.code.java.dataflow.FlowSources

/**
 * * A method call that executes an SQL query, covering both standard JDBC
 * * and the project-specific custom framework.
 * 
 */
abstract class DatabaseExecution extends MethodAccess {
  /** Gets the expression that is being executed as an SQL query or query ID. */
  abstract Expr getSql();
}

/**
 * * Models a standard JDBC execution call.
 * 
 */
class StandardJdbcExecution extends DatabaseExecution {
  StandardJdbcExecution() {
    exists(Method m | m = this.getMethod() |
      // Sink 1: Preparing a statement on a Connection object.
      m.getDeclaringType().getASupertype*().hasQualifiedName("java.sql", "Connection") and
      m.hasName("prepareStatement")
      or
      // Sink 2: Executing a query on a Statement object.
      m.getDeclaringType().getASupertype*().hasQualifiedName("java.sql", "Statement") and
      m.getName().regexpMatch("execute.*|addBatch")
    )
  }

  override Expr getSql() { result = this.getArgument(0) }
}

/**
 * * Models a custom framework execution call from `DefaultDBAccess`.
 * 
 */
class CustomFrameworkExecution extends DatabaseExecution {
  CustomFrameworkExecution() {
    this.getMethod()
        .getDeclaringType()
        .hasQualifiedName("com.tsystems.dao.xml.db", "DefaultDBAccess") and
    (
      this.getMethod().hasName("createPreparedStatement") or
      this.getMethod().hasName("execute") or
      this.getMethod().hasName("executePreparedStatement")
    )
  }

  override Expr getSql() { result = this.getArgument(0) }
}

/**
 * * Configuration for tracking taint from SQL sources to execution sinks.
 * 
 */
class SqlTaintTrackingConfig extends TaintTracking::Configuration {
  SqlTaintTrackingConfig() { this = "SqlTaintTrackingConfig" }

  /**
   *   * Defines the sources of SQL queries or query identifiers.
   *   
   */
  override predicate isSource(DataFlow::Node source) {
    exists(Expr e | source.asExpr() = e |
      // Source 1: A raw string literal containing case-insensitive SQL keywords.
      exists(StringLiteral sl | sl = e |
        sl.getValue()
            .regexpMatch("(?i).*\\b(SELECT|INSERT|UPDATE|DELETE|WITH|FETCH|VALUES|FROM|@param|MERGE|INTO|LEFT|JOIN|ORDER BY|GROUP BY|DELETE FROM|INSERT INTO|UPDATE SET)\\b.*")
      )
    )
    or
    // Source 2: The declaration of a static final String field that acts as a query ID.
    // Correction: Use `f.getInitializer()` and `cce.getStringValue()`
    exists(Field f, CompileTimeConstantExpr cce |
      source.asExpr() = f.getInitializer() and
      cce = f.getInitializer() and
      f.isStatic() and
      f.isFinal() and
      f.getType() instanceof TypeString and
      // Check that the field is initialized with something that looks like a query ID.
      cce.getStringValue().regexpMatch("^[A-Z_]+(\\.[A-Z_]+)+$")
    )
  }

  /**
   *   * Defines the sinks where SQL queries are executed.
   *   
   */
  override predicate isSink(DataFlow::Node sink) {
    exists(DatabaseExecution exec | sink.asExpr() = exec.getSql())
  }
}

from SqlTaintTrackingConfig config, DataFlow::PathNode source, DataFlow::PathNode sink
where config.hasFlowPath(source, sink)
select "{ " +
    // The output is a single JSON string per finding.
    "\"id\": \"" + sink.getNode().getLocation().toString().replaceAll("file://", "") + "\", " +
    "\"path\": \"" + source.getNode().getLocation().getFile().getAbsolutePath() + "\", " +
    "\"methodName\": \"" + source.getNode().getEnclosingCallable().getName() + "\", " +
    "\"code\": \"" +
    source
        .getNode()
        .toString()
        .replaceAll("\"", "\\\"")
        .replaceAll("\n", "\\n")
        .replaceAll("\r", "\\r") + "\", " + "\"className\": \"" +
    source.getNode().getEnclosingCallable().getDeclaringType().getName() + "\", " +
    "\"sourceExpressionType\": \"" + source.getNode().getType().getName() + "\", " + "\"type\": \"" +
    "STATIC" + "\", " + "\"startLine\": " + source.getNode().getLocation().getStartLine() + ", " +
    "\"startColumn\": " + source.getNode().getLocation().getStartColumn() + ", " + "\"endLine\": " +
    source.getNode().getLocation().getEndLine() + ", " + "\"endColumn\": " +
    source.getNode().getLocation().getEndColumn() + " }"
