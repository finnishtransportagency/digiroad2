(function(root) {
  var urlParts = function() {
    var url = window.location.href.split('/');
    return _.filter(url, function(urlPart) { return !_.isEmpty(urlPart); });
  };

  var name = function() {
    var environmentName = {
      'testiextranet.liikennevirasto.fi': 'production',
      'apptest.liikennevirasto.fi': 'training',
      'devtest.liikennevirasto.fi': 'staging'
    };

    return environmentName[urlParts()[1]] || 'unknown';
  };

  var urlPath = function() {
    var urlWithoutResource = _.initial(urlParts());
    return _.head(urlWithoutResource) + '//' + _.tail(urlWithoutResource).join('/');
  };

  root.Environment = {
    name: name,
    urlPath: urlPath
  };
}(this));