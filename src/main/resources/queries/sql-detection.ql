import java
import semmle.code.java.dataflow.TaintTracking
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.FlowSources
import semmle.code.java.controlflow.Guards

class SqlExecutionMethod extends Method {
    SqlExecutionMethod() {
        exists(RefType t | t.hasQualifiedName("java.sql", "Statement") |
            this.getDeclaringType().getASupertype*() = t and
            (this.getName().matches("execute%") or this.getName().matches("addBatch"))
        )
        or
        exists(RefType t | t.hasQualifiedName("java.sql", "PreparedStatement") |
            this.getDeclaringType().getASupertype*() = t and
            this.getName().matches("execute%")
        )
        or
        // Add other common frameworks here
        exists(RefType t | t.hasQualifiedName("org.springframework.jdbc.core", "JdbcTemplate") |
            this.getDeclaringType().getASupertype*() = t and
            this.getName().matches("query%")
        )
    }
}

class SqlTaintTrackingConfig extends TaintTracking::Configuration {
    SqlTaintTrackingConfig() { this = "SqlTaintTrackingConfig" }

    override predicate isSource(DataFlow::Node source) {
        // A source is any string literal that looks like part of an SQL query
        exists(StringLiteral lit | 
            lit = source.asExpr() and
            lit.getValue().toLowerCase().matches([
                "%select%", "%insert%", "%update%", "%delete%", "%from%", "%where%", 
                "%join%", "%order by%", "%fetch first%", "%with ur%"
            ])
        )
    }

    override predicate isSink(DataFlow::Node sink) {
        // A sink is the first argument to one of our defined SQL execution methods
        exists(MethodAccess ma, SqlExecutionMethod method |
            ma.getMethod() = method and
            sink.asExpr() = ma.getArgument(0)
        )
    }

    override predicate isAdditionalTaintStep(DataFlow::Node node1, DataFlow::Node node2) {
        // Track string concatenation and StringBuilder appends
        exists(AddExpr add | add = node2.asExpr() |
            node1.asExpr() = add.getAnOperand()
        )
        or
        exists(MethodAccess ma | ma.getMethod().getName() = "append" |
            node2.asExpr() = ma and
            node1.asExpr() = ma.getAnArgument()
        )
    }
}

string getSqlQueryType(StringLiteral sql) {
  exists(string val | val = sql.getValue() |
    if (val.matches("%?%") or val.matches("%:%") or val.matches("%#{%") or val.matches("%${%"))
    then result = "PARAMETERIZED"

    else if
      exists(BinaryExpr be |
        be instanceof AddExpr and
        be.getAnOperand() = sql
      ) or
      exists(MethodAccess ma |
        ma.getMethod().getName() = "append" and
        DataFlow::localExprFlow(sql, ma.getAnArgument())
      )
    then result = "DYNAMIC"
    
    else result = "STATIC"
  )
}

from SqlTaintTrackingConfig config, DataFlow::PathNode sourceNode, DataFlow::PathNode sinkNode
where config.hasFlowPath(sourceNode, sinkNode)
select 
  "{" +
    "\"id\": \"" + sinkNode.getNode().getLocation().getFile().getAbsolutePath() + ":" + sinkNode.getNode().getLocation().getStartLine() + "\"," +
    "\"path\": \"" + sourceNode.getNode().getLocation().getFile().getAbsolutePath() + "\"," +
    "\"startLine\": " + sourceNode.getNode().getLocation().getStartLine() + "," +
    "\"methodName\": \"" + sourceNode.getNode().getEnclosingCallable().getName() + "\"," +
    "\"code\": \"" + sourceNode.getNode().asExpr().(StringLiteral).getValue().replaceAll("\"", "\\\"") + "\"," +
    "\"sourceExpressionType\": \"" + sourceNode.getNode().asExpr().getType().toString() + "\"," +
    "\"type\": \"" + getSqlQueryType(sourceNode.getNode().asExpr()) + "\"," +
    "\"className\": \"" + sourceNode.getNode().getEnclosingCallable().getDeclaringType().getName() + "\"" +
  "}"  
