# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]

## 1.0.0-rc11

- make optional parameters less restricted

## 1.0.0-rc10

- fix a couple of bugs

## 1.0.0-rc9

- vararg params are now automatically nilable

## 1.0.0-rc8

- fixed bug with path parameters

## 1.0.0-rc7

- added kw-varargs
- added defaults option

## 1.0.0-rc6

- client option is evaluted rather than used as a symbol
- each endpoint function now embeds the function symbol and the arguments it was passed into the request map using keys `::name` and `::args`.  This is to help
with all kinds of caching jobs.
- it is not longer legal to skip argument vector in endpoint definitions, instead there's a new optional spec that can be supplied after function symbol,
that is applied to conformed argument list. This enables one to spec relations of two parameters. It's essentially `(s/& (s/cat param spec) additional-spec)`.

## 1.0.0-rc5

- fix a big mistake

## 1.0.0-rc4

- added new param meta tags `:form` and `:form-map` to denote form params. Default behaviour of `:body` is now
to be added to request as a body.
- `:json-bodies` opt has been removed and `:jsonify-bodies` opt has been added. The new opt regulates the default
behaviour of converting body parameters to json.
- `:client` has been added, which is a fn that is invoked on the map before it's returned. Defaults to `identity` but
this is the perfect place to stick in your client function if you're so inclined.

## 1.0.0-rc3

- improve date unforming, default implementatin now returns a `java.time` object on best effort basis
- when specifying path parameters at path definition site, param symbols are no longer needed.
e.g `["{id}" id ::id]` is now shortened to `["{id}" ::id]`. 

## 1.0.0-rc2

- add unform to date conformer
- added ability to specify path parameters for whole subtree at a single site

## 1.0.0-rc1

- fix error when parameter vector was missing
- fix error when nil parameter is passed to parameter marked with `:json`
- spec coercion is now used on passed parameters, which is quite a breaking change, but it unified a lot of use-cases, like date formatting.
- removed :json meta, this is now handled by conformers, added utility functions for adding json conformers to param specs

## 1.0.0-beta3

- added a new option, `value-transform`, allows user to specify a general transformation for parameter values.
- added a default implementation of `value-transform`, see README
