# rest2java
A maven plugin for generating a RESTful Java Client from a JSON specification.

Manually writing a Java Client for a RESTful API is a difficult and time consuming exercise for all but the simplest APIs.
This maven plugin takes a JSON description of a RESTful API as an input, and generates Java source code to act as a client.
The sourcecode generated makes it easy to discover the API without having to refer to the original manual, see examples below to see the beautiful Client APIs which can be generated.

Simply add the following maven plugin to your pom.xml file:
```xml
<insertXml>here</insertXml>
```

The examples below demonstrate what this plugin can do:

##Example 1

```code
https://api.mysite.com/submit_name?api_key=myApiKey&name=bob&current_age=65
```

The above URL allows the posting of a name (required) and age (option), together with an API key. We can documented as follows:

```json
{  
   "apiName":"MySite",
   "defaultBaseUrl":"https://api.mysite.com",
   "mandatoryPermaParams":[  
      {  
         "type":"java.util.String",
         "javaName":"apiKey",
         "jsonName":"api_key"
      }
   ],
   "methods":[  
      {  
         "methodName":"submitName",
         "mandatoryParameters":[  
            {  
               "type":"java.util.String",
               "javaName":"name"
            }
         ],
         "optionalParameters":[  
            {  
               "type":"java.util.Integer",
               "javaName":"currentAge",
               "jsonName":"current_age"
            }
         ],
         "url":"submit_name"
      }
   ]
}
```
The result is this fluent api:

```java
MySite template = new MySite(String api);
JsonNode node = template.submitName("bob").withCurrentAge(65).do();
```
Notice how all the mandatory parameters are part of the method, and any additional optional parameters come afterwards.

##Limitations:
Current the api only support JsonNode return types, but the design of this tool will allow us to evolve this over time.
