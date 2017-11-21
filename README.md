# Naptime #

*Making REST APIs so easy, you can build them in your sleep!*

[![Build Status](https://travis-ci.org/coursera/naptime.svg)](https://travis-ci.org/coursera/naptime)

> *Project Status*: **Alpha**. Although Coursera has been using Naptime in production since 2013,
  and is responsible for nearly 100% of our traffic, expect a number of API and binary
  incompatibilities until we reach beta.

Coursera leverages interactive clients to provide a high quality learning experience. In practice,
this means Javascript clients for the web, and native apps for iOS and Android. In order to avoid
duplicated work, we re-use the same APIs across web and mobile clients. This has forced us to build
generic, re-usable APIs. Naptime is the result of these efforts. Naptime helps server-side
developers build canonical, RESTful, re-usable APIs.

## Why Naptime? ##

We adopted Play! and Scala as part of our migration away from PHP & Python. We initially built APIs
using the stock Play! APIs. Play!'s APIs are powerful and general, but we found we could trade off
some of that power for a big gain in productivity and REST standardization. We believed that an
opinionated, optimized framework could DRY out our code, and increase developer productivity.
After a number of false starts, we built Naptime. Today, over 95% of new APIs at Coursera are built
using Naptime. Developers like using Naptime because it helps them get their job done more quickly.

## Naptime Principles ##

We've attempted to follow a few principles to help guide development. They are roughly:

 - **Developer Productivity**: Naptime is optimized for developer productivity above all else.
 - **Canonical/Standardized**: Naptime codifies a set of conventions to reduce ambiguity in generic
   REST APIs. It's much faster for both API and client developers to work on a myriad of products
   and features across our platform with these standardized APIs.
 - **Type Safety**: In our experience, due to the nature of our product, we've found that leveraging
   the compiler to catch bugs early is better for developers, in addition to taking advantage of
   IDEs (e.g. autocomplete) and other tooling.
 - **Performance**: If developers have to hack around performance problems, that ends up making more
   work not less. Naptime performance should be good, without compromising developer productivity.
 - **Easy Learning**: We strongly avoid DSLs and symbol operators. Additionally, despite leveraging
   advanced Scala capabilities (e.g. macros, path dependent types, the type-class pattern, etc.) to
   power the library, authoring a REST API should not require knowledge of quasiquotes or other
   advanced language features.

## Using Naptime ##

To learn more about how to use naptime, check out our 
[getting started guide](http://coursera.github.io/naptime/gettingstarted/)!
