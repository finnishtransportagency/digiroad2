(function(root) {
  var environmentProperty = function() {
    var properties = {
      production: 'UA-57190819-4',
      training: 'UA-57190819-3',
      staging: 'UA-57190819-2',
      unknown: 'UA-57190819-1'
    };
    return properties[Environment.name()];
  };

  var environmentConfiguration = function() {
    var configurations = {
      production: 'auto',
      training: 'auto',
      staging: 'auto',
      unknown: 'none'
    };
    return configurations[Environment.name()];
  };

  var start = function() {
    ga('create', environmentProperty(), environmentConfiguration());
    ga('send', 'pageview');
  };

  root.Analytics = {
    start: start
  };
}(this));