(function(root) {
  root.TileMapSelector = function(container) {
    var element =
    '<div class="tileMapSelector">' +
      '<div class="bg"></div>' +
      '<div class="content">' +
        '<ul>' +
          '<li data-layerid="base_2" title="Maastokartta">Maastokartta</li>' +
          '<li data-layerid="24" title="Ortokuvat">Ortokuvat</li>' +
          '<li data-layerid="base_35" title="Taustakarttasarja" class="selected">Taustakarttasarja</li>' +
        '</ul>' +
      '</div>' +
    '</div>';
    container.append(element);
    var contentContainer = container.find('.tileMapSelector .content');
    contentContainer.find('li').click(function(event) {
      contentContainer.find('li.selected').removeClass('selected');
      $(event.target).addClass('selected');
    });
  };
})(this);