package com.bpk.cljcamel;

import org.apache.camel.builder.RouteBuilder;
import clojure.lang.IFn;

public class RouteBuilderImpl extends RouteBuilder
{
    private final IFn fn;

    public RouteBuilderImpl(IFn fn)
    {
        this.fn = fn;
    }

    public void configure()
    {
        fn.invoke(this);
    }
}
