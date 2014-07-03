var http = require('http');
var util = require('util');
var httpProxy = require('http-proxy');

var VALLU_API_URL = process.env.VALLU_API_URL;

var proxy = httpProxy.createProxyServer({
  secure: false
});

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
      proxy.web(req, res, { target: VALLU_API_URL });
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
