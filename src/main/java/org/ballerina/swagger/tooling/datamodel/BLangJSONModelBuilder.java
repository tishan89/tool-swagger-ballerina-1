/*
*  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package org.ballerina.swagger.tooling.datamodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wso2.ballerina.core.interpreter.*;
import org.wso2.ballerina.core.model.*;
import org.wso2.ballerina.core.model.expressions.*;
import org.wso2.ballerina.core.model.invokers.MainInvoker;
import org.wso2.ballerina.core.model.statements.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Stack;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


/**
 * Serializes ballerina language object model to JSON based model.
 */
public class BLangJSONModelBuilder implements NodeVisitor {

    private JsonObject jsonObj;
    private Stack<JsonArray> tempJsonArrayRef = new Stack<>();
    private boolean isExprAsString = true;
    private BLangExpressionModelBuilder exprVisitor;

    public BLangJSONModelBuilder(JsonObject jsonObj) {
        this.exprVisitor = new BLangExpressionModelBuilder();
        this.jsonObj = jsonObj;
    }

    @Override
    public void visit(BallerinaFile bFile) {

        tempJsonArrayRef.push(new JsonArray());

        //package definitions
        JsonObject pkgDefine = new JsonObject();
        pkgDefine.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.PACKAGE_DEFINITION);
        pkgDefine.addProperty(BLangJSONModelConstants.PACKAGE_NAME, bFile.getPackageName());
        tempJsonArrayRef.peek().add(pkgDefine);

        //import declarations
        if (bFile.getImportPackages() != null) {
            for (ImportPackage anImport : bFile.getImportPackages()) {
                anImport.accept(this);
            }
        }
        
        ArrayList<PositionAwareNode> rootElements = new ArrayList<>();
    
        if (bFile.getConstants() != null && bFile.getConstants().length > 0) {
            for (Const constDefinition : bFile.getConstants()) {
                rootElements.add(constDefinition);
            }
        }
        
        if (bFile.getServices() != null) {
            Service[] services = new Service[bFile.getServices().size()];
            bFile.getServices().toArray(services);
            for (Service service : services) {
                rootElements.add(service);
            }
        }

        for (Function function : bFile.getFunctions()) {
            BallerinaFunction bFunction = (BallerinaFunction) function;
            rootElements.add(bFunction);
        }


        if (bFile.getConnectors() != null) {
            for (BallerinaConnector connector : bFile.getConnectors()) {
                connector.accept(this);
            }
        }

        Collections.sort(rootElements, new Comparator<PositionAwareNode>() {
            @Override
            public int compare(PositionAwareNode o1, PositionAwareNode o2) {
                return Integer.compare(o1.getRelativePosition(), o2.getRelativePosition());
            }
        });

        //service definitions //connector definitions //function definition
        for (PositionAwareNode node : rootElements) {
            node.accept(this);
        }

        this.jsonObj.add(BLangJSONModelConstants.ROOT, tempJsonArrayRef.peek());
        this.tempJsonArrayRef.pop();

    }

    @Override
    public void visit(ImportPackage importPackage) {
        JsonObject importObj = new JsonObject();
        importObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.IMPORT_DEFINITION);
        importObj.addProperty(BLangJSONModelConstants.IMPORT_PACKAGE_NAME, importPackage.getName());
        importObj.addProperty(BLangJSONModelConstants.IMPORT_PACKAGE_PATH, importPackage.getPath());
        tempJsonArrayRef.peek().add(importObj);
    }

    @Override
    public void visit(Service service) {
        JsonObject serviceObj = new JsonObject();
        serviceObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.SERVICE_DEFINITION);
        serviceObj.addProperty(BLangJSONModelConstants.SERVICE_NAME, service.getSymbolName().getName());
        tempJsonArrayRef.push(new JsonArray());
        if (service.getResources() != null) {
            for (Resource resource : service.getResources()) {
                resource.accept(this);
            }
        }
        tempJsonArrayRef.push(new JsonArray());
        if (service.getAnnotations() != null) {
            for (Annotation annotation : service.getAnnotations()) {
                annotation.accept(this);
            }
        }
        serviceObj.add(BLangJSONModelConstants.ANNOTATION_DEFINITIONS, this.tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        if (service.getConnectorDcls() != null) {
            for (ConnectorDcl connectDcl : service.getConnectorDcls()) {
                connectDcl.accept(this);
            }
        }
        if (service.getVariableDcls() != null) {
            for (VariableDcl variableDcl : service.getVariableDcls()) {
                variableDcl.accept(this);
            }
        }
        serviceObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(serviceObj);
    }

    @Override
    public void visit(BallerinaConnector connector) {
        JsonObject jsonConnectObj = new JsonObject();
        jsonConnectObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE,
                BLangJSONModelConstants.CONNECTOR_DEFINITION);
        jsonConnectObj.addProperty(BLangJSONModelConstants.CONNECTOR_NAME, connector.getConnectorName().getName());
        tempJsonArrayRef.push(new JsonArray());
        tempJsonArrayRef.push(new JsonArray());
        if (connector.getAnnotations() != null) {
            for (Annotation annotation : connector.getAnnotations()) {
                annotation.accept(this);
            }
        }
        jsonConnectObj.add(BLangJSONModelConstants.ANNOTATION_DEFINITIONS, this.tempJsonArrayRef.peek());
        this.tempJsonArrayRef.pop();
        if (connector.getParameters() != null) {
            for (Parameter parameter : connector.getParameters()) {
                parameter.accept(this);
            }
        }
        if (connector.getConnectorDcls() != null) {
            for (ConnectorDcl connectDcl : connector.getConnectorDcls()) {
                connectDcl.accept(this);
            }
        }
        if (connector.getVariableDcls() != null) {
            for (VariableDcl variableDcl : connector.getVariableDcls()) {
                variableDcl.accept(this);
            }
        }
        if (connector.getActions() != null) {
            for (BallerinaAction action : connector.getActions()) {
                action.accept(this);
            }
        }
        jsonConnectObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(jsonConnectObj);
    }

    @Override
    public void visit(Resource resource) {
        JsonObject resourceObj = new JsonObject();
        resourceObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.RESOURCE_DEFINITION);
        resourceObj.addProperty(BLangJSONModelConstants.RESOURCE_NAME, resource.getName());
        tempJsonArrayRef.push(new JsonArray());
        tempJsonArrayRef.push(new JsonArray());
        if (resource.getResourceAnnotations() != null) {
            for (Annotation annotation : resource.getResourceAnnotations()) {
                annotation.accept(this);
            }
        }
        resourceObj.add(BLangJSONModelConstants.ANNOTATION_DEFINITIONS, this.tempJsonArrayRef.peek());
        this.tempJsonArrayRef.pop();
        if (resource.getParameters() != null) {
            for (Parameter parameter : resource.getParameters()) {
                parameter.accept(BLangJSONModelBuilder.this);
            }
        }
        if (resource.getWorkers() != null) {
            resource.getWorkers().forEach(new Consumer<Worker>() {
                @Override
                public void accept(Worker worker) {
                    worker.accept(BLangJSONModelBuilder.this);
                }
            });
        }
        if (resource.getConnectorDcls() != null) {
            for (ConnectorDcl connectDcl : resource.getConnectorDcls()) {
                connectDcl.accept(this);
            }
        }
        if (resource.getVariableDcls() != null) {
            for (VariableDcl variableDcl : resource.getVariableDcls()) {
                variableDcl.accept(BLangJSONModelBuilder.this);
            }
        }
        if(resource.getResourceBody() != null) {
            resource.getResourceBody().accept(this);
        }
        resourceObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(resourceObj);
    }

    @Override
    public void visit(BallerinaFunction function) {
        JsonObject jsonFunc = new JsonObject();
        jsonFunc.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.FUNCTION_DEFINITION);
        jsonFunc.addProperty(BLangJSONModelConstants.FUNCTIONS_NAME, function.getFunctionName());
        jsonFunc.addProperty(BLangJSONModelConstants.IS_PUBLIC_FUNCTION, function.isPublic());
        this.tempJsonArrayRef.push(new JsonArray());
        this.tempJsonArrayRef.push(new JsonArray());
        if (function.getAnnotations() != null) {
            for (Annotation annotation : function.getAnnotations()) {
                annotation.accept(this);
            }
        }
        jsonFunc.add(BLangJSONModelConstants.ANNOTATION_DEFINITIONS, this.tempJsonArrayRef.peek());
        this.tempJsonArrayRef.pop();
        if (function.getVariableDcls() != null) {
            for (VariableDcl variableDcl : function.getVariableDcls()) {
                variableDcl.accept(BLangJSONModelBuilder.this);
            }
        }
        if (function.getParameters() != null) {
            for (Parameter parameter : function.getParameters()) {
                parameter.accept(this);
            }
        }
        if (function.getConnectorDcls() != null) {
            for (ConnectorDcl connectDcl : function.getConnectorDcls()) {
                connectDcl.accept(this);
            }
        }
        JsonObject returnTypeObj = new JsonObject();
        returnTypeObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.RETURN_TYPE);
        JsonArray returnTypeArray = new JsonArray();
        if (function.getReturnParameters() != null) {
            for (Parameter parameter : function.getReturnParameters()) {
                JsonObject typeObj = new JsonObject();
                typeObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.RETURN_ARGUMENT);
                typeObj.addProperty(BLangJSONModelConstants.PARAMETER_TYPE, parameter.getType().toString());
                if (parameter.getName() != null) {
                    typeObj.addProperty(BLangJSONModelConstants.PARAMETER_NAME, parameter.getName().toString());
                }
                returnTypeArray.add(typeObj);
            }
        }
        returnTypeObj.add(BLangJSONModelConstants.CHILDREN, returnTypeArray);
        tempJsonArrayRef.peek().add(returnTypeObj);
        function.getCallableUnitBody().accept(this);
        jsonFunc.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(jsonFunc);
    }

    @Override
    public void visit(BTypeConvertor typeConvertor) {

    }

    @Override
    public void visit(BallerinaAction action) {
        JsonObject jsonAction = new JsonObject();
        jsonAction.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.ACTION_DEFINITION);
        jsonAction.addProperty(BLangJSONModelConstants.ACTION_NAME, action.getName());
        tempJsonArrayRef.push(new JsonArray());
        tempJsonArrayRef.push(new JsonArray());
        if (action.getAnnotations() != null) {
            for (Annotation annotation : action.getAnnotations()) {
                annotation.accept(this);
            }
        }
        jsonAction.add(BLangJSONModelConstants.ANNOTATION_DEFINITIONS, this.tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        if (action.getParameters() != null) {
            for (Parameter parameter : action.getParameters()) {
                parameter.accept(this);
            }
        }
        if (action.getVariableDcls() != null) {
            for (VariableDcl variableDcl : action.getVariableDcls()) {
                variableDcl.accept(this);
            }
        }
        if (action.getConnectorDcls() != null) {
            for (ConnectorDcl connectDcl : action.getConnectorDcls()) {
                connectDcl.accept(this);
            }
        }

        JsonObject returnTypeObj = new JsonObject();
        returnTypeObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.RETURN_TYPE);
        JsonArray returnTypeArray = new JsonArray();
        if (action.getReturnParameters() != null) {
            for (Parameter parameter : action.getReturnParameters()) {
                JsonObject typeObj = new JsonObject();
                typeObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.RETURN_ARGUMENT);
                typeObj.addProperty(BLangJSONModelConstants.PARAMETER_TYPE, parameter.getType().toString());
                if (parameter.getName() != null) {
                    typeObj.addProperty(BLangJSONModelConstants.PARAMETER_NAME, parameter.getName().toString());
                }
                returnTypeArray.add(typeObj);
            }
        }
        returnTypeObj.add(BLangJSONModelConstants.CHILDREN, returnTypeArray);
        tempJsonArrayRef.peek().add(returnTypeObj);

        action.getCallableUnitBody().accept(this);
        jsonAction.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(jsonAction);
    }

    @Override
    public void visit(Worker worker) {
        JsonObject jsonWorker = new JsonObject();
        jsonWorker.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.WORKER_DEFINITION);
        tempJsonArrayRef.push(new JsonArray());
        if (worker.getConnectorDcls() != null) {
            for (ConnectorDcl connectDcl : worker.getConnectorDcls()) {
                connectDcl.accept(this);
            }
        }
        if (worker.getVariables() != null) {
            for (VariableDcl variableDcl : worker.getVariables()) {
                variableDcl.accept(this);
            }
        }
        if (worker.getStatements() != null) {
            for (Statement statement : worker.getStatements()) {
                if (isExprAsString) {
                    JsonObject jsonObject = new JsonObject();
                    statement.accept(exprVisitor);
                    jsonObject.addProperty(BLangJSONModelConstants.STATEMENT,
                            exprVisitor.getBuffer().toString());
                    tempJsonArrayRef.peek().add(jsonObject);
                } else {
                    statement.accept(this);
                }
            }
        }
        jsonWorker.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(jsonWorker);
    }

    @Override
    public void visit(Annotation annotation) {
        JsonObject jsonAnnotation = new JsonObject();
        jsonAnnotation.addProperty(BLangJSONModelConstants.DEFINITION_TYPE,
                BLangJSONModelConstants.ANNOTATION_DEFINITION);
        jsonAnnotation.addProperty(BLangJSONModelConstants.ANNOTATION_NAME, annotation.getName());
        jsonAnnotation.addProperty(BLangJSONModelConstants.ANNOTATION_VALUE, annotation.getValue());
        this.tempJsonArrayRef.push(new JsonArray());
        if (annotation.getKeyValuePairs() != null) {
            annotation.getKeyValuePairs().forEach(new BiConsumer<String, String>() {
                @Override
                public void accept(String k, String v) {
                    JsonObject pair = new JsonObject();
                    pair.addProperty(k, v);
                    tempJsonArrayRef.peek().add(pair);
                }
            });
        }
        jsonAnnotation.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        this.tempJsonArrayRef.pop();
        this.tempJsonArrayRef.peek().add(jsonAnnotation);
    }

    @Override
    public void visit(Parameter parameter) {
        JsonObject paramObj = new JsonObject();
        paramObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE, BLangJSONModelConstants.PARAMETER_DEFINITION);
        paramObj.addProperty(BLangJSONModelConstants.PARAMETER_NAME, parameter.getName().getName());
        paramObj.addProperty(BLangJSONModelConstants.PARAMETER_TYPE, parameter.getType().toString());
        this.tempJsonArrayRef.push(new JsonArray());
        if (parameter.getAnnotations() != null) {
            for (Annotation annotation : parameter.getAnnotations()) {
                annotation.accept(this);
            }
        }
        paramObj.add(BLangJSONModelConstants.CHILDREN, this.tempJsonArrayRef.peek());
        this.tempJsonArrayRef.pop();
        this.tempJsonArrayRef.peek().add(paramObj);
    }

    @Override
    public void visit(ConnectorDcl connectorDcl) {
        JsonObject connectObj = new JsonObject();
        connectObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE,
                BLangJSONModelConstants.CONNECTOR_DECLARATION);
        connectObj.addProperty(BLangJSONModelConstants.CONNECTOR_DCL_NAME, connectorDcl.getConnectorName().getName());
        connectObj.addProperty(BLangJSONModelConstants.CONNECTOR_DCL_PKG_NAME, connectorDcl.getConnectorName().getPkgName());
        connectObj.addProperty(BLangJSONModelConstants.CONNECTOR_DCL_VARIABLE, connectorDcl.getVarName().getName());
        this.tempJsonArrayRef.push(new JsonArray());
        if (connectorDcl.getArgExprs() != null) {
            for (Expression expression : connectorDcl.getArgExprs()) {
                expression.accept(this);
            }
        }
        connectObj.add(BLangJSONModelConstants.CHILDREN, this.tempJsonArrayRef.peek());
        this.tempJsonArrayRef.pop();
        this.tempJsonArrayRef.peek().add(connectObj);
    }

    @Override
    public void visit(VariableDcl variableDcl) {
        JsonObject variableDclObj = new JsonObject();
        variableDclObj.addProperty(BLangJSONModelConstants.DEFINITION_TYPE,
                BLangJSONModelConstants.VARIABLE_DECLARATION);
        variableDclObj.addProperty(BLangJSONModelConstants.VARIABLE_NAME, variableDcl.getName().getName());
        variableDclObj.addProperty(BLangJSONModelConstants.VARIABLE_TYPE, variableDcl.getType().toString());
        tempJsonArrayRef.peek().add(variableDclObj);
    }

    @Override
    public void visit(BlockStmt blockStmt) {
        if (blockStmt.getStatements() != null) {
            for (Statement statement : blockStmt.getStatements()) {
                statement.accept(this);
            }
        }
    }

    @Override
    public void visit(AssignStmt assignStmt) {
        JsonObject assignmentStmtObj = new JsonObject();
        assignmentStmtObj.addProperty(BLangJSONModelConstants.STATEMENT_TYPE,
                BLangJSONModelConstants.ASSIGNMENT_STATEMENT);
        tempJsonArrayRef.push(new JsonArray());

        JsonObject LExprObj = new JsonObject();
        LExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE, "left_operand_expression");
        tempJsonArrayRef.push(new JsonArray());
        for (Expression expression : assignStmt.getLExprs()) {
            expression.accept(this);
        }
        LExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(LExprObj);

        JsonObject RExprObj = new JsonObject();
        RExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE, "right_operand_expression");
        tempJsonArrayRef.push(new JsonArray());
        assignStmt.getRExpr().accept(this);
        RExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(RExprObj);

        assignmentStmtObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(assignmentStmtObj);
    }

    @Override
    public void visit(CommentStmt commentStmt) {
        JsonObject commentStmtObj = new JsonObject();
        commentStmtObj.addProperty(BLangJSONModelConstants.STATEMENT_TYPE,
                BLangJSONModelConstants.COMMENT_STATEMENT);
        commentStmtObj.addProperty(BLangJSONModelConstants.COMMENT_STRING, commentStmt.getComment());
        tempJsonArrayRef.peek().add(commentStmtObj);
    }

    @Override
    public void visit(IfElseStmt ifElseStmt) {
        JsonObject ifElseStmtObj = new JsonObject();
        ifElseStmtObj.addProperty(BLangJSONModelConstants.STATEMENT_TYPE,
                BLangJSONModelConstants.IF_ELSE_STATEMENT);
        tempJsonArrayRef.push(new JsonArray());
        if (ifElseStmt.getThenBody() != null) {
            tempJsonArrayRef.push(new JsonArray());

            JsonObject thenBodyObj = new JsonObject();
            thenBodyObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                    BLangJSONModelConstants.IF_STATEMENT_THEN_BODY);
            tempJsonArrayRef.push(new JsonArray());
            ifElseStmt.getThenBody().accept(this);
            thenBodyObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
            tempJsonArrayRef.pop();
            tempJsonArrayRef.peek().add(thenBodyObj);

            JsonObject ifConditionObj = new JsonObject();
            ifConditionObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                    BLangJSONModelConstants.IF_STATEMENT_IF_CONDITION);
            tempJsonArrayRef.push(new JsonArray());
            ifElseStmt.getCondition().accept(this);
            ifConditionObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
            tempJsonArrayRef.pop();
            tempJsonArrayRef.peek().add(ifConditionObj);

            ifElseStmtObj.add(BLangJSONModelConstants.IF_STATEMENT, tempJsonArrayRef.peek());
            tempJsonArrayRef.pop();
        }
        if (ifElseStmt.getElseBody() != null) {
            tempJsonArrayRef.push(new JsonArray());
            ifElseStmt.getElseBody().accept(this);
            ifElseStmtObj.add(BLangJSONModelConstants.ELSE_STATEMENT, tempJsonArrayRef.peek());
            tempJsonArrayRef.pop();
        }
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(ifElseStmtObj);
    }

    @Override
    public void visit(WhileStmt whileStmt) {
        JsonObject whileStmtObj = new JsonObject();
        whileStmtObj.addProperty(BLangJSONModelConstants.STATEMENT_TYPE,
                BLangJSONModelConstants.WHILE_STATEMENT);
        tempJsonArrayRef.push(new JsonArray());
        whileStmt.getCondition().accept(this);
        if (whileStmt.getBody() != null) {
            whileStmt.getBody().accept(this);
        }
        whileStmtObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(whileStmtObj);
    }

    @Override
    public void visit(FunctionInvocationStmt functionInvocationStmt) {
        JsonObject functionInvcStmtObj = new JsonObject();
        functionInvcStmtObj.addProperty(BLangJSONModelConstants.STATEMENT_TYPE,
                BLangJSONModelConstants.FUNCTION_INVOCATION_STATEMENT);
        tempJsonArrayRef.push(new JsonArray());
        functionInvocationStmt.getFunctionInvocationExpr().accept(this);
        functionInvcStmtObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(functionInvcStmtObj);
    }

    @Override
    public void visit(ActionInvocationStmt actionInvocationStmt) {

    }

    @Override
    public void visit(ReplyStmt replyStmt) {
        JsonObject replyStmtObj = new JsonObject();
        replyStmtObj.addProperty(BLangJSONModelConstants.STATEMENT_TYPE,
                BLangJSONModelConstants.REPLY_STATEMENT);
        if (isExprAsString) {
            replyStmt.accept(exprVisitor);
            String stmtExpression = exprVisitor.getBuffer().toString();
            replyStmtObj.addProperty(BLangJSONModelConstants.EXPRESSION, stmtExpression);
        }
        tempJsonArrayRef.push(new JsonArray());
        replyStmt.getReplyExpr().accept(this);
        replyStmtObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(replyStmtObj);
    }

    @Override
    public void visit(ReturnStmt returnStmt) {
        JsonObject returnStmtObj = new JsonObject();
        returnStmtObj.addProperty(BLangJSONModelConstants.STATEMENT_TYPE,
                BLangJSONModelConstants.RETURN_STATEMENT);
        tempJsonArrayRef.push(new JsonArray());
        if (returnStmt.getExprs() != null) {
            for (Expression expression : returnStmt.getExprs()) {
                expression.accept(this);
            }
        }
        returnStmtObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(returnStmtObj);
    }

    @Override
    public void visit(FunctionInvocationExpr funcIExpr) {
        JsonObject funcInvcObj = new JsonObject();
        funcInvcObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.FUNCTION_INVOCATION_EXPRESSION);
        funcInvcObj.addProperty(BLangJSONModelConstants.FUNCTIONS_NAME,
                funcIExpr.getCallableUnitName().toString());
        tempJsonArrayRef.push(new JsonArray());
        if (funcIExpr.getArgExprs() != null) {
            for (Expression expression : funcIExpr.getArgExprs()) {
                expression.accept(this);
            }
        }
        funcInvcObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(funcInvcObj);
    }

    @Override
    public void visit(ActionInvocationExpr actionIExpr) {
        JsonObject actionInvcObj = new JsonObject();
        actionInvcObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.ACTION_INVOCATION_EXPRESSION);
        actionInvcObj.addProperty(BLangJSONModelConstants.ACTION_NAME,
                actionIExpr.getCallableUnitName().getName());
        actionInvcObj.addProperty(BLangJSONModelConstants.ACTION_PKG_NAME,
                actionIExpr.getCallableUnitName().getPkgName());
        actionInvcObj.addProperty(BLangJSONModelConstants.ACTION_CONNECTOR_NAME,
                actionIExpr.getCallableUnitName().getConnectorName());
        tempJsonArrayRef.push(new JsonArray());
        if (actionIExpr.getArgExprs() != null) {
            for (Expression expression : actionIExpr.getArgExprs()) {
                expression.accept(this);
            }
        }
        actionInvcObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(actionInvcObj);
    }

    @Override
    public void visit(BasicLiteral basicLiteral) {
        JsonObject basicLiteralObj = new JsonObject();
        basicLiteralObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.BASIC_LITERAL_EXPRESSION);
        basicLiteralObj.addProperty(BLangJSONModelConstants.BASIC_LITERAL_TYPE,
                basicLiteral.getType().toString());
        basicLiteralObj.addProperty(BLangJSONModelConstants.BASIC_LITERAL_VALUE,
                basicLiteral.getBValue().stringValue());
        tempJsonArrayRef.peek().add(basicLiteralObj);
    }

    @Override
    public void visit(DivideExpr divideExpr) {
        JsonObject divideExprObj = new JsonObject();
        divideExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                                 BLangJSONModelConstants.DIVISION_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        divideExpr.getLExpr().accept(this);
        divideExpr.getRExpr().accept(this);
        divideExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(divideExprObj);
    }

    @Override
    public void visit(UnaryExpression unaryExpression) {
        JsonObject unaryExpr = new JsonObject();
        unaryExpr.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.UNARY_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        unaryExpression.getRExpr().accept(this);
        unaryExpr.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(unaryExpr);
    }

    @Override
    public void visit(AddExpression addExpr) {
        JsonObject addExprObj = new JsonObject();
        addExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.ADD_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        addExpr.getLExpr().accept(this);
        addExpr.getRExpr().accept(this);
        addExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(addExprObj);
    }

    @Override
    public void visit(SubtractExpression subExpr) {
        JsonObject minusExprObj = new JsonObject();
        minusExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.SUBTRACT_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        subExpr.getLExpr().accept(this);
        subExpr.getRExpr().accept(this);
        minusExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(minusExprObj);
    }

    @Override
    public void visit(MultExpression multExpr) {
        JsonObject multiExprObj = new JsonObject();
        multiExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.MULTIPLY_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        multExpr.getLExpr().accept(this);
        multExpr.getRExpr().accept(this);
        multiExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(multiExprObj);
    }

    @Override
    public void visit(AndExpression andExpr) {
        JsonObject andExprObj = new JsonObject();
        andExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.AND_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        andExpr.getLExpr().accept(this);
        andExpr.getRExpr().accept(this);
        andExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(andExprObj);
    }

    @Override
    public void visit(OrExpression orExpr) {
        JsonObject orExprObj = new JsonObject();
        orExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.OR_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        orExpr.getLExpr().accept(this);
        orExpr.getRExpr().accept(this);
        orExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(orExprObj);
    }

    @Override
    public void visit(EqualExpression equalExpr) {
        JsonObject equalExprObj = new JsonObject();
        equalExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.EQUAL_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        equalExpr.getLExpr().accept(this);
        equalExpr.getRExpr().accept(this);
        equalExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(equalExprObj);
    }

    @Override
    public void visit(NotEqualExpression notEqualExpression) {
        JsonObject notequalExprObj = new JsonObject();
        notequalExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.NOT_EQUAL_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        notEqualExpression.getLExpr().accept(this);
        notEqualExpression.getRExpr().accept(this);
        notequalExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(notequalExprObj);
    }

    @Override
    public void visit(GreaterEqualExpression greaterEqualExpression) {
        JsonObject greaterEqualExprObj = new JsonObject();
        greaterEqualExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.GREATER_EQUAL_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        greaterEqualExpression.getLExpr().accept(this);
        greaterEqualExpression.getRExpr().accept(this);
        greaterEqualExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(greaterEqualExprObj);
    }

    @Override
    public void visit(GreaterThanExpression greaterThanExpression) {
        JsonObject greaterExprObj = new JsonObject();
        greaterExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.GREATER_THAN_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        greaterThanExpression.getLExpr().accept(this);
        greaterThanExpression.getRExpr().accept(this);
        greaterExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(greaterExprObj);
    }

    @Override
    public void visit(LessEqualExpression lessEqualExpression) {
        JsonObject lessEqualExprObj = new JsonObject();
        lessEqualExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.LESS_EQUAL_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        lessEqualExpression.getLExpr().accept(this);
        lessEqualExpression.getRExpr().accept(this);
        lessEqualExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(lessEqualExprObj);
    }

    @Override
    public void visit(LessThanExpression lessThanExpression) {
        JsonObject lessExprObj = new JsonObject();
        lessExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.LESS_THAN_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        lessThanExpression.getLExpr().accept(this);
        lessThanExpression.getRExpr().accept(this);
        lessExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(lessExprObj);
    }

    @Override
    public void visit(VariableRefExpr variableRefExpr) {
        JsonObject variableRefObj = new JsonObject();
        variableRefObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.VARIABLE_REFERENCE_EXPRESSION);
        variableRefObj.addProperty(BLangJSONModelConstants.VARIABLE_REFERENCE_NAME,
                variableRefExpr.getSymbolName().getName());
        tempJsonArrayRef.peek().add(variableRefObj);
    }

    @Override
    public void visit(TypeCastExpression typeCastExpression) {

    }

    @Override
    public void visit(ArrayInitExpr arrayInitExpr) {
        JsonObject arrayInitExprObj = new JsonObject();
        arrayInitExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.ARRAY_INIT_EXPRESSION);
        tempJsonArrayRef.push(new JsonArray());
        if (arrayInitExpr.getArgExprs() != null) {
            for (Expression expression : arrayInitExpr.getArgExprs()) {
                expression.accept(this);
            }
        }
        arrayInitExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(arrayInitExprObj);
    }

    @Override
    public void visit(BacktickExpr backtickExpr) {
        JsonObject backquoteExprObj = new JsonObject();
        backquoteExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.BACK_QUOTE_EXPRESSION);
        backquoteExprObj.addProperty(BLangJSONModelConstants.BACK_QUOTE_ENCLOSED_STRING,
                backtickExpr.getTemplateStr());
        tempJsonArrayRef.peek().add(backquoteExprObj);
    }

    @Override
    public void visit(InstanceCreationExpr instanceCreationExpr) {
        JsonObject instanceCreationExprObj = new JsonObject();
        instanceCreationExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                BLangJSONModelConstants.INSTANCE_CREATION_EXPRESSION);
        instanceCreationExprObj.addProperty(BLangJSONModelConstants.INSTANCE_CREATION_EXPRESSION_INSTANCE_TYPE ,
                instanceCreationExpr.getType().toString());
        tempJsonArrayRef.push(new JsonArray());
        instanceCreationExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(instanceCreationExprObj);
    }

    @Override
    public void visit(MainInvoker mainInvoker) {
        //TODO
    }

    @Override
    public void visit(ResourceInvocationExpr resourceInvokerExpr) {
        //TODO
    }

    @Override
    public void visit(MapInitExpr mapInitExpr) {
        //TODO
    }

    @Override
    public void visit(ConstantLocation constantLocation) {
        //TODO
    }

    @Override
    public void visit(LocalVarLocation localVarLocation) {
        //TODO
    }

    @Override
    public void visit(ConnectorVarLocation connectorVarLocation) {
        //TODO
    }

    @Override
    public void visit(ServiceVarLocation serviceVarLocation) {
        //TODO
    }

    @Override
    public void visit(Const constant) {
        JsonObject constantDefinitionDefine = new JsonObject();
        constantDefinitionDefine.addProperty(BLangJSONModelConstants.DEFINITION_TYPE,
                BLangJSONModelConstants.CONSTANT_DEFINITION);
        constantDefinitionDefine.addProperty(BLangJSONModelConstants.CONSTANT_DEFINITION_BTYPE,
                constant.getType().toString());
        constantDefinitionDefine.addProperty(BLangJSONModelConstants.CONSTANT_DEFINITION_IDENTIFIER,
                constant.getName().toString());
        constantDefinitionDefine.addProperty(BLangJSONModelConstants.CONSTANT_DEFINITION_VALUE,
                ((BasicLiteral)constant.getValueExpr()).getBValue().stringValue());
        tempJsonArrayRef.peek().add(constantDefinitionDefine);
    }

    @Override
    public void visit(ArrayMapAccessExpr arrayMapAccessExpr) {
        JsonObject arrayMapAccessExprObj = new JsonObject();
        arrayMapAccessExprObj.addProperty(BLangJSONModelConstants.EXPRESSION_TYPE,
                                   BLangJSONModelConstants.ARRAY_MAP_ACCESS_EXPRESSION);
        arrayMapAccessExprObj.addProperty(BLangJSONModelConstants.ARRAY_MAP_ACCESS_EXPRESSION_NAME,
                                   arrayMapAccessExpr.getSymbolName().getName());

        tempJsonArrayRef.push(new JsonArray());
        arrayMapAccessExpr.getIndexExpr().accept(this);
        arrayMapAccessExprObj.add(BLangJSONModelConstants.CHILDREN, tempJsonArrayRef.peek());
        tempJsonArrayRef.pop();
        tempJsonArrayRef.peek().add(arrayMapAccessExprObj);
    }

    @Override
    public void visit(KeyValueExpression arrayMapAccessExpr) {
        //TODO
    }
    
    @Override
    public void visit(StructVarLocation structVarLocation) {
        // TODO
    }

    @Override
    public void visit(StructInitExpr structInitExpr) {
        // TODO
    }

    @Override
    public void visit(StructFieldAccessExpr structFieldAccessExpr) {
        // TODO
    }

    @Override
    public void visit(BallerinaStruct ballerinaStruct) {
        // TODO
    }

    @Override
    public void visit(StructDcl structDcl) {
        // TODO
    }
}