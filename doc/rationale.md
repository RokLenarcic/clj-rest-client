# Rationale and Design

I've found myself copying ad-hoc helper functions to deal with REST calls from project to project.

So I decided to make a library.

Goals:

1. Simple
2. Reduces `clj-http` code that keeps appearing with 90% of all API calls
3. Flexible, allows working with weirder signatures and customization or simply working without it
4. Validation with schemas (and interaction with various tools)

The design is a single-purpose library that generates `clj-http` request maps.

The `clj-http` library already offers a quality solution for http client needs, with complex features (like cookie handling, caching, etc..).
If we wrapped that, then we'd need to come up with a solution how to customize the underlying client.

Instead of stacking libraries vertically we stack them horizontally. Output of `clj-rest-client`'s functions
can be piped into `clj-http`. This also affords a wealth of options such as:

- serializing/copying/saving requests before they are send
- modifying requests in unforeseen way before they are sent
- using multiple clients, reusing clients, pools etc...
- user decides if 4xx, 5xx are errors
- user can access all the response data

