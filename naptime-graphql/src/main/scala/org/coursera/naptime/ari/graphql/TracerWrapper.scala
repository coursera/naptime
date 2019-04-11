package org.coursera.naptime.ari.graphql

import io.opentracing.Tracer

/**
 * Wraps an optional opentracing.Tracer, which if provided, will be used to trace
 * GraphQL query executions using sangria-slowlog.
 */
case class TracerWrapper(tracer: Option[Tracer])
