(function(root) {
  root.LocationSearch = function() {
    this.search = function(searchString) {
      return LocationInputParser.parse(searchString);
    };
  };
})(this);
