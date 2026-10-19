@{{componentAnnotation}}
public class {{className}} extends {{handlerBase}}<{{inputType}}, {{outputType}}> {
    @Override protected BasicApiContext preHandler({{inputType}} request) throws {{internalException}} {
        {{validation}}
        Context ctx = new Context();
        ctx.input = request;
        return ctx;
    }
    @Override protected BasicApiContext invokeAtomicApi(BasicApiContext context) throws {{internalException}} {
        // 在这里取得真实 Atomic 并调用，结果放 Context；不在单例字段保存请求状态。
        throw new {{internalException}}("ATOMIC_API_UNIMPLEMENTED: {{className}} underlying capability");
    }
    @Override protected {{outputType}} postHandler(BasicApiContext context) throws {{internalException}} {
        Context ctx = (Context) context;
        if (ctx.result == null) { throw new {{internalException}}("result is null"); }
        return ctx.result;
    }
    private static class Context extends BasicApiContext {
        private {{inputType}} input;
        private {{outputType}} result;
    }
}
