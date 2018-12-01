# REST Client

A Clojure library which converts a REST specification into functions that emit `clj-http` request maps.

Very light-weight. 

**[Change Log](CHANGELOG.md)**

## Quick start

This library requires clojure 1.9.0 or higher.

Add to dependencies:

```clojure
[clj-rest-client "1.0.0-rc7"]
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

You can make the http call immediate by setting it as client function to API definition:

```clojure
(defrest {"http://example.com" {"person" {GET (get-person-by-id [id pos-int?])}}} :client client/request)

(get-person-by-id 3)
```

## Usage

A description of features, options and solutions for common needs. 

### Definition format

#### Paths and overall structure

Definition is a nested map where keys are string paths or symbol, keyword HTTP methods.

```clojure
{"path"
  {"subpath" {DELETE ...}
   GET ...
   :post ...}}
``` 

This nested structure defines endpoints for GET and POST methods at "/path". Path parts should not start or end with `/`.
It also continues nesting and defines endpoint for "/path/subpath" for DELETE method.

Values for path parts are another path map, values for methods are endpoint function definitions 


Symbol or keyword keys define signature for a HTTP method for this subpath. E.g.

```clojure
(defrest {"http://example.com" 
           {"person" 
             {"{id}" 
               {GET (get-person-by-id [id pos-int? detail-level pos-int?] {:as :bytes})}}}})
```

Here the GET symbol key is followed by an endpoint definition, which defines a function for GET method for `http://example.com/person` subpath.

Method can be specified equally as `GET` or `get` or `:get`.

The string keys in the nested definition map are there to denote subpaths. They shouldn't start or end with `/`.

**In this particular example the root string key also defines protocol, server, port.**

**This is in no way required. You can define a REST with relative paths only.**

It will be demonstated later how to approach using definitions without predefined host.

#### Endpoint definition

```clojure
{GET (get-example any? [id pos-int? time inst?] {:as :bytes})}
```

The endpoint definition is a list or vector (in this example a list), which contains the following:

- a symbol, name of function to be declared
- an optional spec that is applied to conformed parameter list
- an optional argument vector, defaults to `[]`, must contain alternating parameter names and parameter specs. The vector may contain symbol `&`, all argument
specs following the symbol are added as kw-varargs on the generated function.

- an optional non-vector expression that evaluates to a map, defaults to `{}`, contains additional properties for returned request map, 
it can use parameters in definition

In this example, the spec applied to parameter list `[id time]` will be `(s/& (s/cat :id pos-int? :time inst?) any?)`.

Due to optionals the following definition is also legal:

```clojure
{GET (get-all [])}
```

### Parameter Specs

Parameter specs are applied to parameters with conform. This enables you to use `(s/conforming ...)` in parameter vector to convert types before they are sent.

There are a few premade conformers in `clj-rest-client.conform` namespace that you can combine with `s/and` to do formatting. Usually you'll use `:refer` directive.

```
(:require [clj-rest-client.conform :refer [->json ->json? ->json* ->json?* ->date-format])
```

#### Date formatting

This will format `java.util.Date` and `java.time` objects using the given formatter.

```clojure
(defrest {"example" {GET (example [date (->date-format DateTimeFormatter/ISO_DATE_TIME)])}})
```

#### ->json, ->json*

Sometimes you need to convert a query parameter or a part of a bigger parameter to JSON. Use this conformer:

```clojure
(defrest {"example" {GET (example [inline-query-map (s/and map? ->json)])}})
```

Function `->json*` works the same, but it takes a cheshire opt map.

#### ->json?, ->json?*

The previous example spec doesn't take `nil` values, but if you changed `map?` to `(s/nillable map?)` then invoking the function
with `nil` would produce query parameter `inline-query-map=null`. If you want `nil` to stay `nil` (and thus be eliminated from
query parameters) then use var with question mark:

```clojure
(defrest {"example" {GET (example [inline-query-map (s/and (s/nillable map?) ->json?)])}})
```

### Query parameters

All parameters defined default to being sent as query parameters, unless otherwise specified. Query parameters that are `nil` are
absent.

Description of other parameter types follows. 

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

You can specify format of common parameters in a map key, by using vector instead of string:

So instead of doing:

```clojure
(defrest {"patient"
           {"{id}/{type}"
             {GET (get-patient [id pos-int? type string? detail pos-int?])
              POST (upsert-patient [id pos-int? type string? patient ::patient])}}})
```

you can do:

```clojure
(defrest {"patient"
           {["{id}/{type}" pos-int? string?]
             {GET (get-patient [detail pos-int?])
              POST (upsert-patient [patient ::patient])}}})
```

Here common path parameter was moved into the path spec, but the generated functions are the same.
Every function on that subtree gets that parameter prepended.

### Parameter annotations

Parameters in parameter vector can be annotated.

```clojure
[id pos-int? ^:+ password string? ^:body report bytes?]
```

#### :body

Adds body param to request. See options for specifics.

#### :form

Add this parameter to form params. The name of actual form parameter is calculated using same transformation as query parameters.

#### :form-map

Adds this parameter as a map of form params.

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
(defrest {} :param-transform identity :json-responses true :jsonify-bodies :smart :client identity)
```

#### client

This option specifies function that is invoked with clj-http maps generated by api functions. Defaults to identity.
This is a good place to put your http client function if you want requests to be executed immediately.

There are benefits to separating request generation and request execution, thus the default being identity function.

#### param-transform

This option specifies function that is uset to transform query parameter names: parameter (symbol) -> query parameter name (string).

This is useful to transform clojure's kebab-case symbol names to camel case param names.

#### val-transform

This option specifies a function that is applied to all arguments after argument spec and conform and before being embedded into
request map. It's a function of two arguments: param name symbol and param value, returns new param value. 

Default implementation replaces keyword params with their name string. It's available (for delegating purposes) as `default-val-transform` in core namespace.

#### json-responses

If true then all requests specify `{:as :json}` and all responses are expected to be json responses. Default true.

#### jsonify-bodies

Set to `:always`, `:smart`, `:never`. Body params will be ran through serializer if set to `:always`. 
Option `:smart` will not run string bodies through JSON serializer. Defaults to :smart.

#### defaults

Defaults option should resolve to a map. This map is included in every request map as a baseline. Defaults to `{}`

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

Or simply find the minority cases and use the extras map to override the default:

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
