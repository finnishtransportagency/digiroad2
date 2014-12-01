(function(root){
  var name = function() {
    var environmentName = {
      'testiextranet.liikennevirasto.fi': 'production',
      'apptest.liikennevirasto.fi': 'training',
      'devtest.liikennevirasto.fi': 'staging'
    };

    var url = window.location.href.split('/');
    var urlParts = _.filter(url, function(urlPart) { return !_.isEmpty(urlPart); });
    return environmentName[urlParts[1]] || 'unknown';
  };

  root.Environment = {
    name: name
  };
}(this));