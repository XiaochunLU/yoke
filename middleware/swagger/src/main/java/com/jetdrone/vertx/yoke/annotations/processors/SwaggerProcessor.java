package com.jetdrone.vertx.yoke.annotations.processors;

import com.jetdrone.vertx.yoke.annotations.*;
import com.jetdrone.vertx.yoke.middleware.Swagger;
import com.jetdrone.vertx.yoke.middleware.YokeRequest;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SwaggerProcessor extends AbstractAnnotationHandler<Swagger> {

    public SwaggerProcessor() {
        super(Swagger.class);
    }

    @Override
    public void process(Swagger swagger, Object instance, Class<?> clazz, Method method) {

        StringBuilder sb;

        SwaggerResource res = Processor.getAnnotation(clazz, SwaggerResource.class);

        if (res == null) {
            return;
        }

        // create the resource
        final Swagger.Resource resource = swagger.createResource(res.path(), res.description());

        Produces clazzProducesAnn = Processor.getAnnotation(clazz, Produces.class);
        Consumes clazzConsumesAnn = Processor.getAnnotation(clazz, Consumes.class);

        String[] clazzProduces = clazzProducesAnn != null ? clazzProducesAnn.value() : null;
        String[] clazzConsumes = clazzConsumesAnn != null ? clazzConsumesAnn.value() : null;

        if (clazzProduces != null) {
            resource.produces(clazzProduces);
        }

        if (clazzConsumes != null) {
            resource.consumes(clazzConsumes);
        }

        Produces producesAnn = Processor.getAnnotation(method, Produces.class);
        Consumes consumesAnn = Processor.getAnnotation(method, Consumes.class);

        String[] produces = producesAnn != null ? producesAnn.value() : null;
        String[] consumes = consumesAnn != null ? consumesAnn.value() : null;

        if (produces == null) {
            produces = clazzProduces;
        }
        if (consumes == null) {
            consumes = clazzConsumes;
        }

        // register the produces
        if (produces != null) {
            resource.produces(produces);
        }

        // register the consumes
        if (consumes != null) {
            resource.consumes(produces);
        }

        SwaggerDoc doc = Processor.getAnnotation(method, SwaggerDoc.class);
        Deprecated deprecated = Processor.getAnnotation(method, Deprecated.class);

        if (doc == null) {
            return;
        }

        // create operations json
        final JsonObject operations = new JsonObject();

        // add all notes
        if (doc.notes().length > 0) {
            sb = new StringBuilder();
            for (String s : doc.notes()) {
                sb.append(s);
                sb.append(' ');
            }

            String finalNotes = sb.toString();
            int len = finalNotes.length() - 1;
            len = len > 0 ? len : 0;
            finalNotes = finalNotes.substring(0, len);
            operations.putString("notes", finalNotes);
        }

        // add nickname (deducted from the method name)
        operations.putString("nickname", method.getName());

        // TODO: authorizations

        // add parameters
        if (doc.parameters().length > 0) {
            JsonArray jsonParameters = new JsonArray();
            operations.putArray("parameters", jsonParameters);

            for (Parameter parameter : doc.parameters()) {
                jsonParameters.addObject(parseParameter(parameter));
            }
        }

        // add response messages
        if (doc.responseMessages().length > 0) {
            JsonArray jsonResponseMessages = new JsonArray();
            operations.putArray("responseMessages", jsonResponseMessages);

            for (ResponseMessage responseMessage : doc.responseMessages()) {
                jsonResponseMessages.addObject(
                        new JsonObject()
                                .putNumber("code", responseMessage.code())
                                .putString("message", responseMessage.message())
                );
            }
        }

        // produces
        if (produces != null) {
            operations.putArray("produces", new JsonArray(produces));
        }

        // consumes
        if (consumes != null) {
            operations.putArray("consumes", new JsonArray(consumes));
        }

        if (deprecated != null) {
            // TODO: once SWAGGER API changes this should be boolean
            operations.putString("deprecated", "true");
        }

        // process the methods that have both YokeRequest and Handler

        if (Processor.isCompatible(method, ALL.class, YokeRequest.class, Handler.class)) {
            resource.all(Processor.getAnnotation(method, ALL.class).value(), doc.summary(), operations);
        }
        if (Processor.isCompatible(method, CONNECT.class, YokeRequest.class, Handler.class)) {
            resource.connect(Processor.getAnnotation(method, CONNECT.class).value(), doc.summary(), operations);
        }
        if (Processor.isCompatible(method, OPTIONS.class, YokeRequest.class, Handler.class)) {
            resource.options(Processor.getAnnotation(method, OPTIONS.class).value(), doc.summary(), operations);
        }
        if (Processor.isCompatible(method, HEAD.class, YokeRequest.class, Handler.class)) {
            resource.head(Processor.getAnnotation(method, HEAD.class).value(), doc.summary(), operations);
        }
        if (Processor.isCompatible(method, GET.class, YokeRequest.class, Handler.class)) {
            resource.get(Processor.getAnnotation(method, GET.class).value(), doc.summary(), operations);
        }
        if (Processor.isCompatible(method, POST.class, YokeRequest.class, Handler.class)) {
            resource.post(Processor.getAnnotation(method, POST.class).value(), doc.summary(), operations);
        }
        if (Processor.isCompatible(method, PUT.class, YokeRequest.class, Handler.class)) {
            resource.put(Processor.getAnnotation(method, PUT.class).value(), doc.summary(), operations);
        }
        if (Processor.isCompatible(method, PATCH.class, YokeRequest.class, Handler.class)) {
            resource.patch(Processor.getAnnotation(method, PATCH.class).value(), doc.summary(), operations);
        }
        if (Processor.isCompatible(method, DELETE.class, YokeRequest.class, Handler.class)) {
            resource.delete(Processor.getAnnotation(method, DELETE.class).value(), doc.summary(), operations);
        }
    }

    @Override
    public void process(Swagger swagger, Object instance, Class<?> clazz, Field field) {
        // NOOP
    }

    private JsonObject parseParameter(Parameter parameter) {

        final JsonObject response = new JsonObject();

        // must be lower case
        response.putString("paramType", parameter.paramType().name().toLowerCase());
        response.putString("name", parameter.name());
        // recommended
        String description = parameter.description();
        if (description != null && !description.equals("")) {
            response.putString("description", parameter.description());
        }
        // optional
        response.putBoolean("required", parameter.required());
        response.putBoolean("allowMultiple", parameter.allowMultiple());

        // describe the type

        // required
        response.putString("type", parameter.type() == Parameter.Type.FILE ? parameter.type().name() : parameter.type().name().toLowerCase());
        // TODO: $ref
        if (parameter.format() != Parameter.Format.UNDEFINED) {
            response.putString("type", parameter.format().name().toLowerCase().replace('_', '-'));
        }
        // TODO: defaultValue
        // TODO: enum
        if (!parameter.minimum().equals("")) {
            response.putString("type", parameter.minimum());
        }
        if (!parameter.maximum().equals("")) {
            response.putString("type", parameter.maximum());
        }
        if (parameter.type() == Parameter.Type.ARRAY) {
            // TODO: items
            response.putBoolean("uniqueItems", parameter.uniqueItems());
        }

        return response;
    }
}