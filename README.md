# Javalin-Annotation-Processor
Processor to provide basic endpoint annotations to Javalin.
Non-void methods annotated with `@Endpoint` will be converted into non-blocking endpoints.
Additionally, classes annotated with @Endpoint will prepend the endpoints of all annotated methods with the provided path.

Note that void methods will require a Context parameter, this is enforced by the processor. 
Non-void methods can choose whether or not the Context should be provided.

The processor supports the following HTTP methods : Get, Post, Put, Patch, Delete, Head, Options.
Additionally it also supports Javalin specific methods : Before, After.


## Example
```java
@Endpoint("/root")
public class ClassExample {
  
  @Endpoint("/")
  public String root() {
    return "Sample result";
   }
   
   @Endpoint("/:id")
   public String rootId(Context ctx) {
    return "Sample result with param : " + ctx.pathParam("id");
   }
   
   @Endpoint("/:id/sub")
   public void rootIdSub(Context ctx) {
    ctx.result("Parameter was : " + ctx.pathParam("id"));
   }
   
   @Endpoint("/:id", MethodType.PATCH)
   public static void updateRootId(Context ctx) {
    String body = ctx.body();
    // ... Do something
    ctx.result("Patched object with ID " + ctx.pathParam("id"));
   }
}
```
