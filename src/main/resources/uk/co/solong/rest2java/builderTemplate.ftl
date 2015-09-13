
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
import static org.springframework.http.HttpMethod.*;


import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public final class ${method.methodName?cap_first}Builder {
    private RestTemplate restTemplate;
    private Map<String,Object> parameters;
    private Map<String,Object> postParameters;
    private HttpMethod methodType = ${method.methodType!"GET"};
    
   
    <#assign includeComma=false>
    <#if mandatoryPermaParams??>
        <#if method.mandatoryParameters??>
            <#if (((method.mandatoryParameters?size) > 0) && ((mandatoryPermaParams?size) > 0)) >
                <#assign includeComma=true>
            </#if>
        </#if>
    </#if>
    public ${method.methodName?cap_first}Builder(<#list mandatoryPermaParams as mandatoryPermaParam>final ${mandatoryPermaParam.type} ${mandatoryPermaParam.javaName}<#sep>,</#list><#if includeComma>,</#if> <#if method.mandatoryParameters??><#list method.mandatoryParameters as mandatoryParameter> final ${mandatoryParameter.type} ${mandatoryParameter.javaName}<#sep>,</#list></#if>) {
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
        for (final String t : parameters.keySet()) {
            Object maybeParam = parameters.get(t);
            if (maybeParam != null) {
                b.queryParam(t, maybeParam.toString());
            }
        }

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
        for (final String t : parameters.keySet()) {
            Object maybeParam = parameters.get(t);
            if (maybeParam != null) {
                b.queryParam(t, maybeParam.toString());
            }
        }

        final String uriString = b.build().toUriString();
        if (HttpMethod.POST.equals(methodType)) {
            final UriComponentsBuilder postParamBuilder = UriComponentsBuilder.fromUriString("").path("");
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
        for (final String t : parameters.keySet()) {
            Object maybeParam = parameters.get(t);
            if (maybeParam != null) {
                b.queryParam(t, maybeParam.toString());
            }
        }

        final String uriString = b.build().toUriString();
        if (HttpMethod.POST.equals(methodType)) {
        
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
         
            final UriComponentsBuilder postParamBuilder = UriComponentsBuilder.fromUriString("http://placeholder.com");
            for (final String t : postParameters.keySet()) {
                Object maybeParam = postParameters.get(t);
                if (maybeParam != null) {
                    postParamBuilder.queryParam(t, maybeParam.toString());
                }
            }
            final String postParameterString = postParamBuilder.build().toUriString().replaceAll("http://placeholder.com\\?", "");
            
            HttpEntity<String> entity = new HttpEntity<String>(postParameterString, headers);
            final JsonNode result = restTemplate.postForObject(uriString, entity, JsonNode.class, parameters);
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
        parameters.put("${optionalParameter.jsonName}", ${optionalParameter.javaName});
        </#if>
        return this;
    }
    </#list>
}
</@output>
</#list>