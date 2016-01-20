(function(root) {
  var urlParts = function() {
    var url = window.location.href.split('/');
    return _.filter(url, function(urlPart) { return !_.isEmpty(urlPart); });
  };

  var name = function() {
    var environmentName = {
      'extranet.liikennevirasto.fi': 'production',
      'testiextranet.liikennevirasto.fi': 'production', // change to 'integration' once new production in use
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