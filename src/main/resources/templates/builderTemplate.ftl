
<#list methods as method>
<@output file="${package}.${method.methodName?cap_first}Builder">
package ${package};

import java.util.HashMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpMethod;
import java.lang.String;
import java.util.Map;

public final class ${method.methodName?cap_first}Builder {
    private RestTemplate restTemplate;
    private Map<String,Object> parameters;
    private Map<String,Object> postParameters;
    private HttpMethod methodType = ${method.methodType!"GET"};
    public ${method.methodName?cap_first}Builder(<#list mandatoryPermaParams as mandatoryPermaParam>final ${mandatoryPermaParam.type} _${mandatoryPermaParam.javaName}<#sep>,</#list>, <#list method.mandatoryParameters as mandatoryParameter> final ${mandatoryParameter.type} ${mandatoryParameter.javaName}<#sep>,</#list>) {
        restTemplate = new RestTemplate();
        parameters = new HashMap<String,Object>();
        postParameters = new HashMap<String,Object>();
        
        //mandatoryPermaParams
        <#list mandatoryPermaParams as mandatoryPermaParam>
        <#if mandatoryPermaParam.isPostParam??>
        postParameters.put("${mandatoryPermaParam.jsonName}", ${mandatoryPermaParam.javaName});
        <#else>
        parameters.put("${mandatoryPermaParam.jsonName}", ${mandatoryPermaParam.javaName});
        </#if>
        </#list>
        
        //mandatoryParams
        <#list method.mandatoryParameters as mandatoryParameter>
        <#if mandatoryParameter.isPostParam??>
        postParameters.put("${mandatoryParameter.jsonName}", ${mandatoryParameter.javaName});
        <#else>
        parameters.put("${mandatoryParameter.jsonName}", ${mandatoryParameter.javaName});
        </#if>
        </#list>
        
        //fixedParameters
        <#list method.fixedParameters as fixedParameter>
        <#if fixedParameter.isPostParam??>
        postParameters.put("${fixedParameter.jsonName}", ${fixedParameter.jsonValue});
        <#else>
        parameters.put("${fixedParameter.jsonName}", "${fixedParameter.jsonValue}");
        </#if>
        </#list>
    }
    <#--
    public String asObject() {
        final UriComponentsBuilder b = UriComponentsBuilder.fromUriString("https://api.linode.com/").path("");
        for (final String t : parameters.keySet()) b.queryParam(t, parameters.get(t).toString());

        final String uriString = b.build().toUriString();
        if (HttpMethod.POST.equals(methodType)) {
            final String result = restTemplate.postForObject(uriString, postParameters, java.lang.String.class, parameters);
            return result;
        } else {
            final String result = restTemplate.getForObject(uriString, java.lang.String.class, parameters);
            return result;
        }
        
    }-->
    public String asString() {
        final UriComponentsBuilder b = UriComponentsBuilder.fromUriString("${defaultBaseUrl}").path("${method.url}");
        for (final String t : parameters.keySet()) b.queryParam(t, parameters.get(t).toString());

        final String uriString = b.build().toUriString();
        if (HttpMethod.POST.equals(methodType)) {
            final UriComponentsBuilder postParamBuilder = UriComponentsBuilder.fromUriString().path();
            for (final String t : postParameters.keySet()) postParamBuilder.queryParam(t, postParameters.get(t).toString());
            final String postParameterString = postParamBuilder.build().toUriString();
            
            final String result = restTemplate.postForObject(uriString, postParameterString, java.lang.String.class, parameters);
            return result;
        } else {
            final String result = restTemplate.getForObject(uriString, java.lang.String.class, parameters);
            return result;
        }
    }
    
    public JsonNode asJson() {
        final UriComponentsBuilder b = UriComponentsBuilder.fromUriString("${defaultBaseUrl}").path("${method.url}");
        for (final String t : parameters.keySet()) b.queryParam(t, parameters.get(t).toString());

        final String uriString = b.build().toUriString();
        if (HttpMethod.POST.equals(methodType)) {
        
            final UriComponentsBuilder postParamBuilder = UriComponentsBuilder.fromUriString().path();
            for (final String t : postParameters.keySet()) postParamBuilder.queryParam(t, postParameters.get(t).toString());
            final String postParameterString = postParamBuilder.build().toUriString();
            final JsonNode result = restTemplate.postForObject(uriString, postParameterString, JsonNode.class, parameters);
            return result;
        } else {
            final JsonNode result = restTemplate.getForObject(uriString, JsonNode.class, parameters);
            return result;
        }
    }
    
    <#list method.optionalParameters as optionalParameter>
    public ${method.methodName?cap_first}Builder with${optionalParameter.javaName?cap_first}(final ${optionalParameter.type} ${optionalParameter.javaName}) {
        <#if optionalParameter.isPostParam??>
        postParameters.put("${optionalParameter.jsonName}", ${optionalParameter.javaName});
        <#else>
        parameters.put("${optionalParameter.jsonName}", "${optionalParameter.javaName}");
        </#if>
        return this;
    }
    </#list>
}
</@output>
</#list>