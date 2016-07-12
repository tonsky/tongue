#!/usr/local/bin/node

process.env.TZ = "UTC";

var fs = require('fs'),
    vm = require('vm');

global.goog = {};

global.CLOSURE_IMPORT_SCRIPT = function(src) {
  require('./target/none/goog/' + src);
  return true;
};

function nodeGlobalRequire(file) {
  vm.runInThisContext.call(global, fs.readFileSync(file), file);
}

nodeGlobalRequire('./target/test.js');

var res = tongue.test.test_all();
if (res.fail + res.error > 0)
  process.exit(1);
