# REST Client

A Clojure library which converts a REST specification into functions that emit `clj-http` request maps.

Very light-weight. 

**[Change Log](CHANGELOG.md)**

## Quick start

This library requires Clojure 1.9.0 or higher.

Add to dependencies:

```clojure
[clj-rest-client "1.0.0"]
```

In your namespace add the dependency:

```clojure
(ns example.core
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-http.client :as client]))
```

Define a rest interface:

```clojure
(defrest {"http://example.com" {"person" {GET (get-person-by-id [id pos-int?])}}})
```

This defines a function named **`get-person-by-id`** that you can now call and it will return a `clj-http` compatible map.

You can then run the request by using `clj-http`'s request function:

```clojure
(client/request (get-person-by-id 3))
```

The function is spec instrumented and will raise an error if parameters aren't valid by spec.

You can make the HTTP call immediate by adding an HTTP request executing function as a client function to API definition:

```clojure
(defrest {"http://example.com" {"person" {GET (get-person-by-id [id pos-int?])}}} :client client/request)

(get-person-by-id 3)
```

**All data structure merges in this framework are deep merges using `meta-merge` library. See https://github.com/weavejester/meta-merge for specifics.**

## Usage

A series of tutorials using GitHub v3 API as basis:

#### [Tutorial 1 - First Request](doc/t1/t1.md)
#### [Tutorial 2 - Parameter types](doc/t2/t2.md)
#### [Tutorial 3 - Client object](doc/t3/t3.md)
#### [Tutorial 4 - Parameter Specs](doc/t4/t4.md)
#### [Tutorial 5 - Varargs parameters](doc/t5/t5.md)
#### [Tutorial 6 - Splitting definition](doc/t6/t6.md)
#### [Tutorial 7 - A different way to describe API](doc/t7/t7.md)

A description of features, options and solutions for common use-cases.

**This framework allows multiple different ways to specify things so I urge you to read all the tutorials**

### Definition format

#### Paths and overall structure

The definition is a nested map where keys are string paths or symbols/keywords HTTP methods.

```clojure
{"path"
  {"subpath" {DELETE ...}
   GET ...
   :post ...}}
``` 

This nested structure defines endpoints for GET and POST methods at "/path". Path parts should not start or end with `/`.
It also continues nesting and defines an endpoint for "/path/subpath" for DELETE method.

Values for path parts are another path map, values for methods are endpoint function definitions 


Symbol or keyword keys define the signature for an HTTP method for this subpath. E.g.

```clojure
(defrest {"http://example.com" 
           {"person" 
             {"{id}" 
               {GET (get-person-by-id [id pos-int? detail-level pos-int?] {:as :bytes})}}}})
```

Here the GET symbol key is followed by an endpoint definition, which defines a function for GET method for `http://example.com/person` subpath.

The method can be specified equally as `GET` or `get` or `:get`.

The string keys in the nested definition map are there to denote subpaths. They shouldn't start or end with `/`.

**In this particular example the root string key also defines the protocol, server, port.**

**This is in no way required. You can define a REST with relative paths only.**

It is demonstrated in our tutorials how to approach using definitions without a pre-defined host.

#### Endpoint definition

```clojure
{GET (get-example any? [id pos-int? time inst?] {:as :bytes})}
```

The endpoint definition is a list or vector (in this example a list), which contains the following:

- a symbol, name of a function to be declared
- an optional spec that is applied to the conformed parameter list
- an argument vector; must contain alternating parameter names and parameter specs. The vector may contain symbol `&`, all argument
specs following the symbol are added as kw-varargs on the generated function with provided specs wrapped in `nilable`.

- an optional non-vector expression that evaluates to a map, defaults to `{}`, contains additional properties for returned request map, 
it can use parameters in the definition

In this example, the spec applied to parameter list `[id time]` will be `(s/& (s/cat :id pos-int? :time inst?) any?)`.

Due to optional parts of the definition, the following definition is also legal:

```clojure
{GET (get-all [])}
``` 

#### Default method

If the endpoint is the only one at a subpath you can skip specifying the method and it gains the default method:

```clojure
(get-all [])
```

The method given to such endpoint definition is set by `:default-method` `defrest` parameter, defaulting to `:get`.

### defrest options

The macro supports options as varargs key-values.

Here are the options with their defaults:

```clojure
(defrest {} :param-transform identity :json-responses true :jsonify-bodies :smart :post-process-fn identity)
```

#### post-process-fn

This option specifies a function that is invoked after generating clj-http in API function. Defaults to identity.

There are benefits to separating request generation and request execution, thus the default being identity function.

#### default-method 

Sets the default method for endpoints with no method specified, defaults to `:get`

#### param-transform

This option specifies function that is used to transform query parameter names: parameter (symbol) -> query parameter name (string).

This is useful to transform Clojure's kebab-case symbol names to camel case param names.

#### val-transform

This option specifies a function that is applied to all arguments after argument spec and conforming and before being embedded into
the request map. It's a function of two arguments: param name symbol and param value and returns the new param value. 

Default implementation replaces keyword params with their name string. It's available (for delegating purposes) as `default-val-transform` in core namespace.

#### json-responses

If true then all requests specify `{:as :json}` and all responses are expected to be json responses. Defaults to true.

#### jsonify-bodies

Set to `:always`, `:smart`, `:never`. Body params will be ran through serializer if set to `:always`. 
Option `:smart` will not run string bodies through JSON serializer. Defaults to :smart.

#### defaults

The `defaults` option should resolve to a map. This map is included in every request map as a baseline. Defaults to `{}`

#### edn-readers

If the definition is a string (link to EDN file), then this parameter specifies
additional readers besides the default readers of `#crc/ns` and `#crc/ref`.

#### edn-key

When loading EDN use a specific key in loaded EDN, useful when using refs.

#### fdef?

Emit a fdef for generated functions, defaults to false.

## License

Copyright © 2018, 2019 Rok Lenarčič

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
