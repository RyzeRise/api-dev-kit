package dev.atomic;

import java.nio.file.*;
import java.util.*;

final class Fixtures {
  static Path write(Path root, String name, String text) throws Exception {
    Path path = root.resolve("src/main/java/" + name + ".java");
    Files.createDirectories(path.getParent());
    Files.writeString(path, text);
    return path;
  }

  static void framework(Path root) throws Exception {
    write(
        root,
        "com/example/framework/OpsApiException",
        "package com.example.framework; public class OpsApiException extends Exception { public"
            + " OpsApiException(String s){super(s);} public String getErrMsg(){return"
            + " getMessage();} }");
    write(
        root,
        "com/example/framework/ServiceException",
        "package com.example.framework; public class ServiceException extends Exception { public"
            + " ServiceException(String s){super(s);} }");
    write(
        root,
        "com/example/framework/OpsComponentManagement",
        "package com.example.framework; public class OpsComponentManagement { public <T> T"
            + " getFacadeImpl(Class<T> t) {return null;} }");
    write(
        root,
        "com/example/framework/OpsFacadeInterface",
        "package com.example.framework; public @interface OpsFacadeInterface {}");
    write(
        root,
        "com/example/framework/Component",
        "package com.example.framework; public @interface Component {}");
    write(
        root,
        "com/example/framework/Autowired",
        "package com.example.framework; public @interface Autowired {}");
    write(
        root,
        "com/example/framework/ApiHandler",
        "package com.example.framework; public abstract class ApiHandler<I,O> { protected static"
            + " class BasicApiContext {} protected abstract BasicApiContext preHandler(I request)"
            + " throws OpsApiException; protected abstract BasicApiContext"
            + " invokeAtomicApi(BasicApiContext c) throws OpsApiException; protected abstract O"
            + " postHandler(BasicApiContext c) throws OpsApiException; public O run(I request)"
            + " throws OpsApiException {return postHandler(invokeAtomicApi(preHandler(request)));}"
            + " }");
  }

  static Path profile(Path root) throws Exception {
    Path path = root.resolve("framework.yaml");
    Files.writeString(
        path,
        """
        version: 1
        targetJava: 8
        types:
          componentManagement: com.example.framework.OpsComponentManagement
          facadeAnnotation: com.example.framework.OpsFacadeInterface
          handlerBase: com.example.framework.ApiHandler
          internalException: com.example.framework.OpsApiException
          externalException: com.example.framework.ServiceException
          injectionAnnotation: com.example.framework.Autowired
          componentAnnotation: com.example.framework.Component
        facadeDefault: throw
        layout:
          - packagePrefix: com.example
            sourceRoot: src/main/java
        """);
    return path;
  }

  static void external(Path root, boolean multi) throws Exception {
    String op = multi ? "QueryResourceByPortAddressRsp" : "QueryResourceByAddressRsp";
    write(
        root,
        "com/example/model/" + op,
        "package com.example.model; public class "
            + op
            + " { public void setResourceName(String s){} public void setPortName(String s){} }");
    write(
        root,
        "com/example/inventory/PortInfo",
        "package com.example.inventory; public class PortInfo {public String getNeResId(){return"
            + " null;} public String getPortName(){return null;} }");
    write(
        root,
        "com/example/inventory/ResourceInfo",
        "package com.example.inventory; public class ResourceInfo {public String getName(){return"
            + " null;} }");
  }
}
