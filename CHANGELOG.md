# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]

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
