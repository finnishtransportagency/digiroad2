(function(root) {
  root.SearchBox = function() {
    var tooltip = "Hae katuosoitteella, tieosoitteella tai koordinaateilla";
    var groupDiv = $('<div class="panel-group"/>');
    var coordinatesDiv = $('<div class="panel search-box"/>');
    var coordinatesText = $('<input type="text" class="location input-sm" placeholder="Osoite tai koordinaatit" title="' + tooltip + '"/>');
    var moveButton = $('<button class="btn btn-sm btn-primary">Hae</button>');

    this.element = groupDiv.append(coordinatesDiv.append(coordinatesText).append(moveButton));
  };
})(this);
