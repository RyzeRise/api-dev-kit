package {{packageName}};
{{annotations}}
public class {{className}} implements {{interfaceType}} {
    /** 校验请求后调用真实底层能力；未接通时明确失败。 */
    @Override public {{responseType}} {{operation}}({{requestType}} request) {
        if (request == null) throw new IllegalArgumentException("Request must not be null");
        {{requiredFields}}
        throw new UnsupportedOperationException("ATOMIC_API_UNIMPLEMENTED: implement {{operation}} for {{apiVersion}}");
    }
}
