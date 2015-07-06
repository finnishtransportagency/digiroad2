var http = require('http');
var util = require('util');
var request = require('request');

var VALLU_API_URL = process.env.VALLU_API_URL;

var server = http.createServer(function (req, res) {
  if (req.method == 'POST') {
    var body = '';
    req.on('data', function (data) {
      body += data;
    });
    req.on('end', function () {
      console.log(body);
      if (!VALLU_API_URL) {
        res.writeHead(200, {'Content-Type': 'text/xml'});
        res.end(body);
      }
    });
    if (VALLU_API_URL) {
      req.pipe(request({ url: VALLU_API_URL, strictSSL: false },
                       function(error, response, body) {
                         if(error) { console.log("Error when making request:", error, response, body); }
                       })).pipe(res);
    }
  } else {
    console.log('Vallu local test only handles POST request!');
    res.end();
  }
});

var host = process.argv[2];
var port = process.argv[3];
if (VALLU_API_URL) {
  console.log('Using Vallu API URL:', VALLU_API_URL);
} else {
  console.log('Vallu API URL not set')
}
console.log(util.format('Starting echoing vallu test server on %s in port %d', host, port));
server.listen(port, host);
