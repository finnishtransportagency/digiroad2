var http = require('http');
var util = require('util');

var server = http.createServer( function(req, res) {
    if (req.method == 'POST') {
        var body = '';
        req.on('data', function (data) {
            body += data;
        });
        req.on('end', function () {
            res.writeHead(200, {'Content-Type': 'text/xml'});
            res.end(body);
        });
    }
    else
    {
        console.log('Vallu local test only handles POST request!');
        res.end();
    }
});

var host = process.argv[2];
var port = process.argv[3]
console.log(util.format('Starting echoing vallu test server on %s in port %d', host, port));
server.listen(port, host);
