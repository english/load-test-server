# load-test-server

API for https://github.com/english/load-test-client.


## Run a load test
```http
POST /load-tests
Content-Type application/json

{
  "resource": "creditors",
  "action": "index",
  "duration": 5,
  "rate": 2
}
```

## Read load-test results
```http
GET /load-tests
Upgrade: websocket
Connection: Upgrade
...
```

```js
{
  "id": 0,
  "resource": "creditors",
  "action": "index",
  "duration": 5,
  "rate": 2,
  "data-points": [{
    "response-time": 120,
    "status": 200,
    "method": "GET",
    "url": "http://example.com",
    "body": "some body"
  }]
}
...
```

The above will receive all load tests and then every new version of a load test
created.

## License

Copyright © 2015 Jamie English

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
