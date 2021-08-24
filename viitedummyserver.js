var http = require('http');
var util = require('util');
var fs = require('fs');



var server = http.createServer(function (req, res) {
    if (req.method == 'GET' && req.url.includes("viite/api/viite/integration/roadway_changes/changes")) {
        console.log(req.url)
        //"https://devtest.vayla.fi/viite/api/viite/integration/roadway_changes/changes?since=2020-04-29T13:59:59"
        var rawdata = fs.readFileSync('roadway_changes_changes_data.json');
        res.writeHead(200, {'Content-Type': 'text/json'});
        res.end(rawdata);
    }{
        console.log(req.url)
        console.log("unsupported call")
    }
});

var host = process.argv[2];
var port = process.argv[3];
console.log(util.format('Starting echoing viite test server on %s in port %d', host, port));
server.listen(port, host,);