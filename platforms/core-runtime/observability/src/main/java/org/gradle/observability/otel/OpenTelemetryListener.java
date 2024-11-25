/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.observability.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.launcher.daemon.context.DaemonContext;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@ServiceScope(Scope.Global.class)
public class OpenTelemetryListener implements BuildOperationListener {

    final OpenTelemetrySdk openTelemetry;
    final ConcurrentMap<OperationIdentifier, Span> activeSpans = new ConcurrentHashMap<>();

    public OpenTelemetryListener(DaemonContext daemonContext) {
        // Configure the endpoint correctly for your Jaeger HTTP receiver
        SpanExporter exporter = OtlpHttpSpanExporter.getDefault();
        SpanProcessor spanProcessor = BatchSpanProcessor.builder(exporter).build();

        // Define the resource
        Resource resource = Resource.create(
            Attributes.builder()
                .put(AttributeKey.stringKey("service.name"), "Gradle")
                .put(AttributeKey.stringKey("daemon.uid"), daemonContext.getUid())
                .put(AttributeKey.stringKey("daemon.pid"), daemonContext.getPid().toString())
                .build()
        );

        // Set up the SDK with the exporter, processor, and resource
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor)
            .setResource(resource)
            .build();
        this.openTelemetry = OpenTelemetrySdk
            .builder()
            .setTracerProvider(tracerProvider)
            .build();
    }

    public void register(BuildOperationListenerManager listenerManager) {
        listenerManager.addListener(this);
    }

    public void flush() {
        openTelemetry
            .getSdkTracerProvider()
            .forceFlush()
            .join(1, TimeUnit.SECONDS);
    }

    @SuppressWarnings("CallToPrintStackTrace")
    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            Context parentContext = Optional
                .ofNullable(buildOperation.getParentId())
                .map(activeSpans::get)
                .map(span -> Context.current().with(span))
                .orElse(Context.root());



            Span span = openTelemetry
                .getTracer("org.gradle")
                .spanBuilder("Build Operation")
                .setAttribute("operation.id", buildOperation.getId().getId())
                .setAttribute("operation.name", buildOperation.getName())
                .setParent(parentContext)
                .startSpan();
            activeSpans.put(buildOperation.getId(), span);
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {

    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        Span span = activeSpans.remove(buildOperation.getId());
        if (span != null) {
            span.end();
        }
    }
}
