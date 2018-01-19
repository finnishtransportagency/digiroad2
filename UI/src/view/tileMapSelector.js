(function(root) {
  root.TileMapSelector = function(container) {
    var element =
    '<div class="tile-map-selector">' +
      '<ul>' +
        '<li data-layerid="terrain" title="Maastokartta">Maastokartta</li>' +
        '<li data-layerid="aerial" title="Ortokuvat">Ortokuvat</li>' +
        '<li data-layerid="background" title="Taustakarttasarja" class="selected">Taustakarttasarja</li>' +
      '</ul>' +
    '</div>';
    container.append(element);
    container.find('li').click(function(event) {
      container.find('li.selected').removeClass('selected');
      var selectedTileMap = $(event.target);
      selectedTileMap.addClass('selected');
      eventbus.trigger('tileMap:selected', selectedTileMap.attr('data-layerid'));
    });
  };
})(this);