(function(root) {
  var urlParts = function() {
    var url = window.location.href.split('/');
    return _.filter(url, function(urlPart) { return !_.isEmpty(urlPart); });
  };

  var name = function() {
    var environmentName = {
      'extranet.liikennevirasto.fi': 'production',
      'testiextranet.liikennevirasto.fi': 'production', // TODO: change to 'integration' once new production in use
      'apptest.liikennevirasto.fi': 'training',
      'devtest.liikennevirasto.fi': 'staging'
    };

    return environmentName[urlParts()[1]] || 'unknown';
  };

  var urlPath = function() {
    var urlWithoutResource = _.initial(urlParts());
    return _.head(urlWithoutResource) + '//' + _.tail(urlWithoutResource).join('/');
  };

  // Environment name shown next to Digiroad logo
  var localizedName = function() {
    var localizedEnvironmentName = {
      integration: '',  // TODO: change to 'Integraatiotestiympäristö' once new production in use
      production: '',
      training: 'Koulutusympäristö',
      staging: 'Testiympäristö',
      unknown: 'Kehitysympäristö'
    };
    return localizedEnvironmentName[Environment.name()];
  };

  root.Environment = {
    name: name,
    urlPath: urlPath,
    localizedName: localizedName
  };
}(this));