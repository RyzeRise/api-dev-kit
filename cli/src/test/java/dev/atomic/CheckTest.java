package dev.atomic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckTest {
  @TempDir Path root;

  void source(String catchBody) throws Exception {
    Fixtures.framework(root);
    Fixtures.write(root, "com/example/ReqDto", "package com.example; public class ReqDto {}");
    Fixtures.write(
        root,
        "com/example/Facade",
        "package com.example; import com.example.framework.*; public interface Facade {String"
            + " query(ReqDto request) throws OpsApiException;}");
    Fixtures.write(
        root,
        "com/example/Handler",
        "package com.example; import com.example.framework.*; public class Handler extends"
            + " ApiHandler<ReqDto,String> { protected BasicApiContext preHandler(ReqDto r){return"
            + " null;} protected BasicApiContext invokeAtomicApi(BasicApiContext c){return null;}"
            + " protected String postHandler(BasicApiContext c){return null;} }");
    Fixtures.write(
        root,
        "com/example/Impl",
        "package com.example; import com.example.framework.*; public class Impl implements Facade"
            + " {Handler handler; public String query(ReqDto request) throws OpsApiException"
            + " {return handler.run(request);} }");
    Fixtures.write(
        root,
        "com/example/Delegate",
        "package com.example; import com.example.framework.*; public class Delegate"
            + " {OpsComponentManagement manager; public String query(ReqDto request) throws"
            + " ServiceException {Facade facade=manager.getFacadeImpl(Facade.class);try{return"
            + " facade.query(request);}catch(OpsApiException e){"
            + catchBody
            + "}}}");
  }

  @Test
  void discoversFacadeWithoutContract() throws Exception {
    source("throw new ServiceException(e.getErrMsg());");
    assertEquals(1, new Checker().check(SourceIndex.scan(root), null).discoveredMethods());
  }

  @Test
  void rejectsDiscardedInternalMessage() throws Exception {
    source("e.getErrMsg();throw new ServiceException(\"lost\");");
    assertTrue(
        new Checker()
            .check(SourceIndex.scan(root), null).findings().stream()
                .anyMatch(f -> f.rule().equals("API-007") && f.severity().equals("ERROR")));
  }

  @Test
  void combinesValidationOnlyAsWarning() throws Exception {
    source("throw new ServiceException(e.getErrMsg());");
    Fixtures.write(
        root,
        "com/example/Guard",
        "package com.example; import com.example.framework.*; class Guard {void run(String a,String"
            + " b) throws OpsApiException {if(a==null||b==null){throw new"
            + " OpsApiException(\"missing\");}}}");
    assertTrue(
        new Checker()
            .check(SourceIndex.scan(root), null).findings().stream()
                .anyMatch(f -> f.rule().equals("API-008") && f.severity().equals("WARN")));
  }

  @Test
  void noTargetsIsIncomplete() throws Exception {
    assertEquals(2, new Checker().check(SourceIndex.scan(root), null).exitCode(false));
  }

  @Test
  void reportsMalformedSource() throws Exception {
    Fixtures.write(root, "Bad", "class Bad {broken");
    assertEquals(2, new Checker().check(SourceIndex.scan(root), null).exitCode(false));
  }

  @Test
  void includesInheritedFacadeOperations() throws Exception {
    source("throw new ServiceException(e.getErrMsg());");
    Fixtures.write(
        root,
        "com/example/ChildFacade",
        "package com.example; public interface ChildFacade extends Facade {}");
    var delegate = root.resolve("src/main/java/com/example/Delegate.java");
    Files.writeString(
        delegate,
        Files.readString(delegate)
            .replace(
                "Facade facade=manager.getFacadeImpl(Facade.class)",
                "ChildFacade facade=manager.getFacadeImpl(ChildFacade.class)"));
    assertEquals(1, new Checker().check(SourceIndex.scan(root), null).discoveredMethods());
  }

  @Test
  void unrelatedRunMethodDoesNotCountAsHandler() throws Exception {
    source("throw new ServiceException(e.getErrMsg());");
    Fixtures.write(
        root,
        "com/example/Impl",
        "package com.example; import com.example.framework.*; public class Impl implements Facade"
            + " {Runnable task;public String query(ReqDto request)throws"
            + " OpsApiException{task.run();return null;}}");
    assertTrue(
        new Checker()
            .check(SourceIndex.scan(root), null).findings().stream()
                .anyMatch(f -> f.rule().equals("API-004") && f.severity().equals("ERROR")));
  }
}
