package org.coursera.naptime

/**
 * This ari defines the key abstractions for automatic resource inclusion as well as GraphQL.
 *
 * The system is split up into 3 distinct layers:
 *
 * {{{
 * +---------------------------------------------------------------------+
 * |                       Presentation Layer                            |
 * |           GraphQL            |            Naptime HTTP              |
 * +------------------------------+--------------------------------------+
 * |                                                                     |
 * |                      Inclusion Engine                               |
 * |                                                                     |
 * +---------------------------------------------------------------------+
 * |                        Data fetching                                |
 * |         Local execution      |           remote execution           |
 * +=====================================================================+
 * |                                                                     |
 * |                   Unmodified Naptime APIs                           |
 * |                                                                     |
 * +---------------------------------------------------------------------+
 * }}}
 *
 * A request enters the system via the presentation layer (typically parsed from the network). The presentation
 * layer constructs a [[org.coursera.naptime.ari.Request]] and passes that to engine. The engine performs a
 * number of validations against the schema, and then makes a number of requests to  to the Data Fetching layer to
 * assemble all of the (available) data required to construct a response. This is passed back to the presentation
 * layer, which constructs a response to be sent out on the wire.
 *
 * The API between the presentation layer and the inclusion engine is the [[org.coursera.naptime.ari.EngineApi]]
 *
 * The API between the inclusion engine and the data fetching layer is defined by
 * [[org.coursera.naptime.ari.FetcherApi]].
 *
 * BEWARE: This code is currently in a high state of flux. Do not depend upon it unless you are prepared for breakages!
 */
package object ari {

}
