var getScripts = function(urls, callback) {
  if (_.isEmpty(urls)) {
    callback();
  } else {
    $.getScript(_.head(urls), function() {
      getScripts(_.rest(urls), callback);
    });
  }
};
