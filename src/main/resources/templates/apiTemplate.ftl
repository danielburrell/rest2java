<@output file="${package}.${serviceName}">
package ${package};
public final class ${serviceName} {

    <#list mandatoryPermaParams as mandatoryPermaParam>
    private final ${mandatoryPermaParam.type} _${mandatoryPermaParam.javaName};
    </#list>
   
    public Linode(<#list mandatoryPermaParams as mandatoryPermaParam>final ${mandatoryPermaParam.type} ${mandatoryPermaParam.javaName}<#sep>,</#list>) {
        <#list mandatoryPermaParams as mandatoryPermaParam>
        _${mandatoryPermaParam.javaName} = ${mandatoryPermaParam.javaName};
        </#list>
    }
    
    <#list methods as method>
    /**
     */
    public final ${method.methodName?cap_first}Builder ${method.methodName}(<#list method.mandatoryParameters as mandatoryParameter>final ${mandatoryParameter.type} ${mandatoryParameter.javaName}<#sep>,</#list>) {
        final ${method.methodName?cap_first}Builder result = new ${method.methodName?cap_first}Builder(<#list mandatoryPermaParams as mandatoryPermaParam>_${mandatoryPermaParam.javaName}<#sep>,</#list>, <#list method.mandatoryParameters as mandatoryParameter> ${mandatoryParameter.javaName}<#sep>,</#list>);
        return result;
    }
    </#list>
    <#-- 
    /**
     */
    public final TestEchoBuilder testEcho(final String mandatoryValueToEcho) {
        final TestEchoBuilder result = new TestEchoBuilder(_apiKey, mandatoryValueToEcho);
        return result;
    }
    -->
}
</@output>