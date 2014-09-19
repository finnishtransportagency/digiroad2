(function(root) {
  root.TileMapSelector = function(container) {
    var element =
    '<div class="tileMapSelector">' +
      '<div class="bg"></div>' +
      '<div class="content">' +
        '<ul>' +
          '<li data-layerid="base_2" title="Maastokartta">Maastokartta</li>' +
          '<li data-layerid="aerial" title="Ortokuvat">Ortokuvat</li>' +
          '<li data-layerid="background" title="Taustakarttasarja" class="selected">Taustakarttasarja</li>' +
        '</ul>' +
      '</div>' +
    '</div>';
    container.append(element);
    var contentContainer = container.find('.tileMapSelector .content');
    contentContainer.find('li').click(function(event) {
      contentContainer.find('li.selected').removeClass('selected');
      var selectedTileMap = $(event.target);
      selectedTileMap.addClass('selected');
      eventbus.trigger('tileMap:selected', selectedTileMap.attr('data-layerid'));
    });
  };
})(this);