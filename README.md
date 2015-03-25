# rest2java
A maven plugin for generating a RESTful Java Client from a JSON specification.

# Rest2Java early developement and feedback
This plugin is currently in beta and has been developed with a minimumal set of functionality. We welcome any feedback at these early stages. A 1.0.0 release is expected June 2015.

#Background
Manually writing a Java Client for a RESTful API is a tedious, time consuming, and error-prone exercise for all but the simplest APIs.
This maven plugin takes a JSON description of a RESTful API as an input, and generates Java source code to act as a client.
The sourcecode generated makes it easy to discover the API without having to refer to the original manual, see examples below to see the beautiful Client APIs which can be generated.

Simply add the following maven plugin to your pom.xml file:
```xml
<build>
   <plugins>
      <plugin>
         <groupId>uk.co.solong</groupId>
         <artifactId>rest2java-maven-plugin</artifactId>
         <version>0.0.3</version>
         <executions>
            <execution>
               <configuration>
                  <schemaFile>${basedir}/src/main/resources/schema.json</schemaFile>
                  <targetPackage>uk.co.solong.linode4j</targetPackage>
               </configuration>
               <goals>
                  <goal>rest2java</goal>
               </goals>
            </execution>
         </executions>
      </plugin>
   <plugins>
</build>
```

The examples below demonstrate what this plugin can do:

##Simple Example 1

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
JsonNode node = template.submitName("bob").withCurrentAge(65).asJson();
```
Notice how all the mandatory parameters are part of the method, and any additional optional parameters come afterwards.

##Advanced Example 1

In this example we show ways to affect the restful call itself as well as show how optional permenant parameters can be sent.
```json 
{  
   "apiName":"MySite",
   ...
   "optionalPermaParams":[{"javaName":"customHeader","javaName":"custom_header"}]
}
```

```java
MySite template = new MySite(String api);
template.alsoCustomHeader("customHeader");
JsonNode node = template.submitName("bob").withCurrentAge(65)
               .alsoTimeout(5000).alsoHeaders(map).alsoAdditionalParameter("key","value").do();
```
Note that optional parameters have a prefix "with", whereas built-in functions have an "also" prefix allowing a clean separation in IDE autocompletion lists. The following advanced built-in functions exist.
 - The timeout can be set on any restful call using the built-in alsoTimeout() which takes the timeout in milliseconds. The default is 5 seconds.
 - Additional headers can be sent using the built-in alsoHeaders().
 - Individual parameters can be set using alsoAdditionalParameter();
 - Alternatively, multiple parameters can be set at the same time using alsoAdditionalParameters();
 - also() methods on the template object have an effect on all subsequent calls.
 - also() methods on an API call only last for that API call.
