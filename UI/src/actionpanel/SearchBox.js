(function(root) {
  root.SearchBox = function() {
    var tooltip = "Koordinaattien sy&ouml;tt&ouml;: pohjoinen (7 merkki&auml;), it&auml; (6 merkki&auml;). Esim. 6901839, 435323";
    var groupDiv = $('<div class="panel-group"/>');
    var coordinatesDiv = $('<div class="panel search-box"/>');
    var coordinatesText = $('<input type="text" class="location input-sm" placeholder="P, I" title="' + tooltip + '"/>');
    var moveButton = $('<button class="btn btn-sm btn-primary">Hae</button>');

    this.element = groupDiv.append(coordinatesDiv.append(coordinatesText).append(moveButton));
  };
})(this);
