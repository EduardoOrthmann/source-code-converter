/**
 * @name Detect All Database Code and its Sources (Java and XML)
 * @description This query finds all database sinks and traces their data sources,
 * supporting both direct Java string literals and lookups in XML configuration files.
 * @kind path-problem
 * @id java/db-migration-source-sink-analysis
 * @tags db-migration java jdbc xml
 */

import java
import semmle.code.java.dataflow.TaintTracking
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.FlowSources
import semmle.code.xml.XML

class SqlExecutionMethod extends Method {
  SqlExecutionMethod() {
    // Standard JDBC
    exists(RefType t |
      t.hasQualifiedName("java.sql", "Statement") or
      t.hasQualifiedName("java.sql", "PreparedStatement")
    |
      this.getDeclaringType().getASupertype*() = t and
      (this.getName().matches("execute%") or this.getName().matches("addBatch"))
    )
    or
    // Spring JDBC Template
    exists(RefType t | t.hasQualifiedName("org.springframework.jdbc.core", "JdbcTemplate") |
      this.getDeclaringType().getASupertype*() = t and
      this.getName().matches("query%")
    )
    or
    // Custom framework for XML-based queries (e.g., DefaultDBAccess)
    exists(RefType t | t.hasQualifiedName("com.tsystems.dao.xml.db", "DefaultDBAccess") |
      this.getDeclaringType().getASupertype*() = t and
      (
        this.getName() = "createPreparedStatement" or
        this.getName() = "execute" or
        this.getName().matches("execute%")
      )
    )
  }
}

class SqlTaintTrackingConfig extends TaintTracking::Configuration {
  SqlTaintTrackingConfig() { this = "SqlTaintTrackingConfig" }

  override predicate isSource(DataFlow::Node source) {
    exists(StringLiteral lit |
      lit = source.asExpr() and
      lit.getValue()
          .toLowerCase()
          .regexpMatch("(?s).*\\b(select|insert|update|delete|from|where|join|fetch first|with ur|merge into)\\b.*")
    )
  }

  override predicate isSink(DataFlow::Node sink) {
    exists(MethodAccess ma, SqlExecutionMethod method |
      ma.getMethod() = method and
      sink.asExpr() = ma.getAnArgument()
    )
  }

  override predicate isAdditionalTaintStep(DataFlow::Node node1, DataFlow::Node node2) {
    exists(AddExpr add | add = node2.asExpr() | node1.asExpr() = add.getAnOperand())
    or
    exists(MethodAccess ma | ma.getMethod().getName().matches("append|concat") |
      node2.asExpr() = ma and
      (node1.asExpr() = ma.getAnArgument() or node1.asExpr() = ma.getQualifier())
    )
  }
}

string getSqlQueryType(Expr sql) {
  if sql.getAChildExpr*() instanceof MethodAccess and
     (sql.(MethodAccess).getMethod().getName().matches("append|concat"))
  then result = "DYNAMIC"
  else if sql.getAChildExpr*() instanceof AddExpr
  then result = "DYNAMIC"
  else result = "STATIC"
}


abstract class SqlQueryFinding extends Element {
  abstract string getFilePath();
  abstract int getStartLine();
  abstract int getStartColumn();
  abstract int getEndLine();
  abstract int getEndColumn();
  abstract string getMethodName();
  abstract string getCode();
  abstract string getSourceExpressionType();
  abstract string getType();
  abstract string getClassName();

  string getId() { result = this.getFilePath() + ":" + this.getStartLine() + ":" + this.getStartColumn() + ":" + this.getEndLine() + ":" + this.getEndColumn() }

  string toJson() {
    result =
      "{" + "\"id\": \"" + this.getId() + "\"," + "\"path\": \"" + this.getFilePath() + "\"," +
        "\"methodName\": \"" + this.getMethodName() + "\"," + "\"code\": \"" +
        this.getCode().replaceAll("\"", "\\\"").replaceAll("\r", "").replaceAll("\n", "\\n") + "\"," +
        "\"className\": \"" + this.getClassName() + "\"," +
        "\"sourceExpressionType\": \"" + this.getSourceExpressionType() + "\"," + "\"type\": \"" +
        this.getType() + "\"," + "\"startLine\": " + this.getStartLine() + "," +
        "\"startColumn\": " + this.getStartColumn() + "," + "\"endLine\": " + this.getEndLine() + "," +
        "\"endColumn\": " + this.getEndColumn() + "}"
  }
}

class TaintedSqlFinding extends SqlQueryFinding {
  DataFlow::PathNode sourceNode;

  TaintedSqlFinding() {
    exists(SqlTaintTrackingConfig config, DataFlow::PathNode sinkNode |
      config.hasFlowPath(sourceNode, sinkNode) and this = sourceNode.getNode().asExpr()
    )
  }

  override string getFilePath() { result = sourceNode.getNode().getLocation().getFile().getAbsolutePath() }
  override int getStartLine() { result = sourceNode.getNode().getLocation().getStartLine() }
  override int getStartColumn() { result = sourceNode.getNode().getLocation().getStartColumn() }
  override int getEndLine() { result = sourceNode.getNode().getLocation().getEndLine() }
  override int getEndColumn() { result = sourceNode.getNode().getLocation().getEndColumn() }
  override string getMethodName() { result = sourceNode.getNode().getEnclosingCallable().getName() }
  override string getCode() { result = sourceNode.getNode().toString() }
  override string getSourceExpressionType() { result = sourceNode.getNode().getType().toString() }
  override string getType() { result = getSqlQueryType(sourceNode.getNode().asExpr()) }
  override string getClassName() { result = sourceNode.getNode().getEnclosingCallable().getDeclaringType().getName() }
}

/**
 * A finding for an SQL query that originates from an XML file.
 */
class XmlSqlFinding extends SqlQueryFinding {
  MethodAccess ma;
  string sqlText;
  XmlElement statementTag; // Make the statementTag available in the class

  XmlSqlFinding() {
    exists(XmlFile xmlFile, XmlAttribute nameAttr |
      statementTag = xmlFile.getARootElement().getAChild*() and
      statementTag.getName() = "statement" and
      nameAttr = statementTag.getAttribute("name") and
      sqlText = statementTag.getTextValue().trim() and
      exists(Expr queryArg |
        ma.getMethod() instanceof SqlExecutionMethod and
        queryArg = ma.getArgument(0) and
        this = ma and
        (
          queryArg.(StringLiteral).getValue() = nameAttr.getValue() or
          queryArg.(FieldAccess).getField().getInitializer().(CompileTimeConstantExpr).getStringValue() = nameAttr.getValue()
        )
      )
    )
  }

  override string getFilePath() { result = statementTag.getLocation().getFile().getAbsolutePath() }
  override int getStartLine() { result = statementTag.getLocation().getStartLine() }
  override int getStartColumn() { result = statementTag.getLocation().getStartColumn() }
  override int getEndLine() { result = statementTag.getLocation().getEndLine() }
  override int getEndColumn() { result = statementTag.getLocation().getEndColumn() }
  override string getMethodName() { result = ma.getEnclosingCallable().getName() }
  override string getCode() { result = sqlText }
  override string getSourceExpressionType() { result = "String (from XML)" }
  override string getType() { result = "STATIC" }
  override string getClassName() { result = ma.getEnclosingCallable().getDeclaringType().getName() }
}

from SqlQueryFinding finding
select finding.toJson()