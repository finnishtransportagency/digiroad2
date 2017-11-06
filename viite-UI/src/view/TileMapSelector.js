(function(root) {
  root.TileMapSelector = function(container) {
    var element =
    '<div class="tile-map-selector">' +
      '<ul>' +
        '<li data-layerid="terrain" title="Maastokartta">Maastokartta</li>' +
        '<li data-layerid="aerial" title="Ortokuvat">Ortokuvat</li>' +
        '<li data-layerid="background" title="Taustakarttasarja" class="selected">Taustakarttasarja</li>' +
        '<li data-layerid="greyscale" title="Harmaasävy">Harmaasävykartta</li>' +
      '</ul>' +
      '<div class="suravage-visible-wrapper">' +
        '<div class="checkbox">' +
          '<label><input type="checkbox" name="suravageVisible" value="suravageVisible" checked="true" id="suravageVisibleCheckbox">Näytä Suravage-Linkit</label>' +
        '</div>' +
      '</div>' +
    '</div>';
    container.append(element);
    container.find('li').click(function(event) {
      container.find('li.selected').removeClass('selected');
      var selectedTileMap = $(event.target);
      selectedTileMap.addClass('selected');
      eventbus.trigger('tileMap:selected', selectedTileMap.attr('data-layerid'));
    });

    $('#suravageVisibleCheckbox').change(function() {
      eventbus.trigger('suravageRoads:toggleVisibility', this.checked);
      eventbus.trigger("suravageProjectRoads:toggleVisibility", this.checked);
    });
  };
})(this);