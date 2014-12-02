var sys = require('sys');
var exec = require('child_process').exec;
var http = require('http');

var inProgress, anotherAfter;

function runBuild() {
  console.log('Starting build.');
  inProgress = exec("/root/build-site.sh", function (error, stdout, stderr) {
    if (error !== null) { console.log('exec error: ' + error); }

    if (stdout && stdout.length) { console.log(stdout); }
    if (stderr && stderr.length) { console.log(stderr); }

    inProgress = false;
    if (anotherAfter) {
      anotherAfter = false;
      runBuild();
    }
  });
}

http.createServer(function (req, res) {
  res.writeHead(200, {'Content-Type': 'text/plain'});

  if (inProgress) {
    anotherAfter = true;
    res.end('Build in process, placed in queue.\n');
  } else {
    runBuild();
    res.end('Build started.\n');
  }

}).listen(8002, '127.0.0.1');

console.log('Site is up. Trigger a build on /site/build');
