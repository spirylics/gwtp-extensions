/**
 * Copyright 2014 ArcBees Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.gwtplatform.dispatch.rest.delegates.rebind;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.velocity.app.VelocityEngine;

import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.gwtplatform.dispatch.rest.rebind.Parameter;
import com.gwtplatform.dispatch.rest.rebind.action.ActionContext;
import com.gwtplatform.dispatch.rest.rebind.action.ActionDefinition;
import com.gwtplatform.dispatch.rest.rebind.action.ActionGenerator;
import com.gwtplatform.dispatch.rest.rebind.action.ActionMethodGenerator;
import com.gwtplatform.dispatch.rest.rebind.resource.MethodContext;
import com.gwtplatform.dispatch.rest.rebind.resource.MethodDefinition;
import com.gwtplatform.dispatch.rest.rebind.utils.Logger;
import com.gwtplatform.dispatch.rest.shared.RestAction;

import static com.gwtplatform.dispatch.rest.rebind.utils.JPrimitives.classTypeOrConvertToBoxed;

public class DelegateMethodGenerator extends ActionMethodGenerator {
    private static final String TEMPLATE = "com/gwtplatform/dispatch/rest/delegates/rebind/DelegateMethod.vm";
    private static final String ACTION_METHOD_SUFFIX = "$action";

    private DelegateMethodDefinition methodDefinition;

    @Inject
    DelegateMethodGenerator(
            Logger logger,
            GeneratorContext context,
            Set<ActionGenerator> actionGenerators,
            VelocityEngine velocityEngine) {
        super(logger, context, actionGenerators, velocityEngine);
    }

    @Override
    public boolean canGenerate(MethodContext methodContext) {
        setContext(methodContext);

        return hasValidReturnType()
                && hasExactlyOneHttpVerb()
                && canGenerateAction();
    }

    @Override
    public MethodDefinition generate(MethodContext methodContext) throws UnableToCompleteException {
        setContext(methodContext);

        List<Parameter> parameters = resolveParameters();
        List<Parameter> inheritedParameters = resolveInheritedParameters();
        JClassType resultType = classTypeOrConvertToBoxed(getContext().getTypeOracle(), getMethod().getReturnType());
        String returnValue = resolveReturnValue();
        String actionMethodName = getMethod().getName() + ACTION_METHOD_SUFFIX;

        methodDefinition = new DelegateMethodDefinition(getMethod(), parameters, inheritedParameters, resultType,
                returnValue, actionMethodName);
        methodDefinition.addImport(RestAction.class.getName());

        generateAction();
        generateMethods();

        return methodDefinition;
    }

    @Override
    protected String getTemplate() {
        return TEMPLATE;
    }

    @Override
    protected void populateTemplateVariables(Map<String, Object> variables) {
        String resultTypeName = methodDefinition.getResultType().getParameterizedQualifiedSourceName();

        List<Parameter> actionParameters = methodDefinition.getInheritedParameters();
        actionParameters.addAll(methodDefinition.getParameters());

        variables.put("resultType", resultTypeName);
        variables.put("returnType", getMethod().getReturnType().getParameterizedQualifiedSourceName());
        variables.put("returnValue", methodDefinition.getReturnValue());
        variables.put("methodName", getMethod().getName());
        variables.put("actionMethodName", methodDefinition.getActionMethodName());
        variables.put("methodParameters", methodDefinition.getParameters());
        variables.put("actionParameters", actionParameters);
        variables.put("action", methodDefinition.getActionDefinitions().get(0));
    }

    private String resolveReturnValue() {
        String returnValue = null;
        JType returnType = getMethod().getReturnType();
        JPrimitiveType primitiveType = returnType.isPrimitive();

        if (primitiveType != null) {
            if (primitiveType != JPrimitiveType.VOID) {
                returnValue = primitiveType.getUninitializedFieldExpression();
            }
        } else {
            returnValue = "null";
        }

        return returnValue;
    }

    private void generateAction() throws UnableToCompleteException {
        ActionContext actionContext = new ActionContext(getMethodContext(), methodDefinition);
        ActionDefinition definition = generateAction(actionContext);

        methodDefinition.addAction(definition);
    }

    private void generateMethods() throws UnableToCompleteException {
        StringWriter writer = new StringWriter();
        mergeTemplate(writer);

        methodDefinition.setOutput(writer.toString());
    }

    private boolean hasValidReturnType() {
        JType returnType = getMethod().getReturnType();

        if (returnType != null) {
            JClassType restActionType = findType(RestAction.class);
            JClassType classType = returnType.isClassOrInterface();
            JPrimitiveType primitiveType = returnType.isPrimitive();

            return (classType != null && restActionType != null && !classType.isAssignableTo(restActionType))
                    || primitiveType != null;
        }

        return false;
    }
}
