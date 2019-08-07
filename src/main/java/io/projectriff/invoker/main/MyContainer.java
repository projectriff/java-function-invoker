package io.projectriff.invoker.main;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.deployer.ApplicationContainer;
import org.springframework.cloud.function.deployer.FunctionProperties;

public class MyContainer extends ApplicationContainer {


    public MyContainer(FunctionCatalog functionCatalog, FunctionInspector functionInspector, FunctionProperties functionProperties) {
        super(functionCatalog, functionInspector, functionProperties);
    }

    @Override
    public FunctionCatalog getFunctionCatalog() {
        return super.getFunctionCatalog();
    }

    @Override
    public FunctionInspector getFunctionInspector() {
        return super.getFunctionInspector();
    }

    @Override
    public FunctionProperties getFunctionProperties() {
        return super.getFunctionProperties();
    }
}
