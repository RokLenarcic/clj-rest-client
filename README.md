# REST Client

A Clojure library which converts a REST specification into functions that emit `clj-http` request maps.

Very light-weight. 


## Why this library?

**[Rationale and design](doc/rationale.md)**

## Quick start

This library requires clojure 1.9.0 or higher.

Add to dependencies:

```clojure
[clj-rest-client "1.0.0-beta1"]
```

In your namespace add dependency:

```clojure
(ns example.core
  (:require [clj-rest-client.core :refer [defrest]
            [clj-http.client :as client]]))
```

Define a rest interface:

```clojure
(defrest {"http://example.com" {"person" {GET (get-person-by-id [id pos-int?])}}})
```

This defines a function named **`get-person-by-id`** that you can now call and it will return a `clj-http` compatible map.

You can then run the request by using `clj-http`:

```clojure
(client/request (get-person-by-id 3))
```

The function is instrumented, and will raise an error if parameters aren't valid by spec.

## Usage

A description of features, options and solutions for common needs. 

### Definition format

Definition is a nested map. Symbol or keyword keys define signature for a HTTP method for this subpath. E.g.

```clojure
(defrest {"http://example.com" {"person" {GET (get-person-by-id [id pos-int?])}}})
```

Here the GET symbol key is followed by an endpoint definition, which defines a function for GET method for `http://example.com/person` subpath.
It is equivalent to use `GET` or `get` or `:get`.

The string keys in the nested definition map are there to denote subpaths. The shouldn't start or end with `/`.

**In this particular example the root string key also defines protocol, server, port.**

**This is in no way required. You can define a REST with relative paths only.**

It will be demonstated later how to approach using definitions without predefined host.

```clojure
{GET (get-example [id pos-int? time inst?] {:as :bytes})}
```

The endpoint definition is a list or vector (in this example a list), which contains the following:

- a symbol, name of function to be declared
- an optional argument vector, defaults to `[]`, must contain alternating parameter names and parameter specs.
- an expression that evaluates to a map, defaults to `{}`, contains additional properties for returned map, it can use parameters in definition

Due to optionals the following definition is also legal:

```clojure
{GET (get-all)}
```

### Query parameters

All parameters defined default to being sent as query parameters, unless otherwise specified.

Description of other types follows. 

### Path parameters

String keys (paths) support notation for path parameters e.g. `{x}`.

```clojure
(defrest {"a{x}a" {"b{y}b" {:get (exam [x pos-int? y pos-int? z pos-int?])}}})
```

This expands into the following code for url construction:

```clojure
{:url (str "a" x "a/b" y "b"), :query-params (into {} (filter second) {"z" z}), ....
```

Note that `x` and `y` are now used as path parameters, but `z` is a query parameter.

### Parameter annotations

Parameters in parameter vector can be annotated.

```clojure
[id pos-int? ^:+ password string? ^:json context map? ^:body report bytes?]
```

#### :json

Causes query parameter to be rendered into json-string. This can of course lead to huge query parameters.

#### :body

Adds body param to request. See options for specifics.

#### :+

This removes parameter from query parameter list. This is useful for extra parameters that don't end up in resulting request, but are useful when generating it.

```clojure
{"dashboard" {GET (get-articles [^:+ password string?] (when password {:basic-auth ["admin" password]}))}}
```

Annotation ensures password doesn't show up in query params while still being in function signature and useful in its workings.

### defrest options

The macro support options as varargs key-values.

Here's the options with defaults

```clojure
(defrest {} :param-transform identity :json-responses true :json-bodies true :instrument true)
```

#### param-transform

This option specifies function that is transformation: parameter (symbol) -> query parameter name (string).

This is useful to transform clojure's kebab-case symbol names to camel case param names.

#### json-responses

If true then all requests specify `{:as :json}` and all responses are expected to be json responses. Default true.

#### json-bodies

If true then body parameters are sent as to-JSON serialized form params, otherwise body params are simply added to request as `:body`.
Default true.

#### instrument

Every function defined by `defrest` has its own `fdef` args spec. If instrument option is true, then all generated functions are also instrumented.
Defaults to true. 

### Loading definition

So far, `defrest` macro was used with a map literal. 

Actually the `defrest` macro supports three ways of loading definitions:

- map literal
- symbol, loads definition map from var named by symbol
- string, loads definition map from URL in EDN format 

The URL can be any valid `java.net.URL` string such as `http://some/url` or `file:my-file.edn`.
Additionally `classpath:` urls are supported, such as `classpath:my-definition.edn`.
You can add custom protocols via the normal Java custom url handlers and cmd switch `-Djava.protocol.handler.pkgs=org.my.protocols`. 

Note that loads happen at macro expansion time.

### Dealing with endpoints that break pattern

It is common to have an API where 95% of endpoints return JSON (and thus warrants the use of :json-responses option),
and yet have 5% of endpoints where that isn't the case.

One way to deal with this is to simply use two `defrest` with different defaults. E.g.:

```clojure
(defrest {"majority" ... define 95% of endpoints here})
(defrest {"special" ... define 5% of endpoints here} :json-bodies false)
```

Or simply find the minority cases and use the extras array to override the default:

```clojure
(defrest {"special" {"endpoint" {GET (get-file {:as :bytes})}}})
```

Here we override the default `:as :json` for outstanding endpoints on a case by case basis.

### Using definitions with relative paths only

In some cases like GitHub API or some other large vendor, usually the absolute URL of API is static and can be specified in `defrest` map.

But when testing other APIs, it's common to specify relative paths, while the host varies. 

Here's a couple of ways of dealing with that. First it can be beneficial to use loading by symbol to add host to definition separately. 

```clojure
(def relative-api '{"person" {GET (get-person)}})
(def absolute-api {"http://my-server" relative-api})
(defrest absolute-api)
```

Simple yet effective solution is to define a client closure such as this:

```clojure
(ns example.core
  (:require [clj-rest-client.core :refer [defrest]
            [clj-http.client :as client]]))

(defrest {"person" {GET (get-person)}})
(defn client [url] (fn [req] (client/request (update req :url (partial str url "/")))))

(def c (client "http://my-server"))
; execute request
(c (get-person))
```

Another helper is `prefix-middleware` function in clj-rest-client.core, which returns `clj-http` middleware that prefixes
urls with given prefix. Here's an example:

```clojure
(ns example.core
  (:require [clj-rest-client.core :refer [defrest prefix-middleware]
            [clj-http.client :as client]]))

(defrest {"person" {GET (get-person)}})
; execute request with extra middleware
(client/with-additional-middleware [(prefix-middleware "http://my-server")]
  (client/request (get-person)))
```

Or simply use `set!` or `alter-var-root` or `binding` to add prefix middleware to `client/*current-middleware`.

Or you can make basic host a path param. E.g.

```clojure
(defrest {"{url}" {"person" {GET (get-person [url string?])}}})
; execute request
(client/request (get-person "http://my-server"))

```

## License

Copyright © 2018 Rok Lenarčič

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
