(function (root) {
  root.LoadingBarDisplay = function (map, container) {
    var element = '<div class="loadingBar-container"></div>';
    container.append(element);

    eventbus.on('loadingBar:show', function() {
      $('.loadingBar-container').append('<div class="loadingBar"></div>');
    });

    eventbus.on('loadingBar:hide', function() {
      $('.loadingBar').remove();
    });

  };
})(this);